package io.nextflow.gradle.registry

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.npr.api.schema.v1.CreatePluginReleaseRequest
import io.seqera.npr.api.schema.v1.CreatePluginReleaseResponse
import io.seqera.npr.api.schema.v1.UploadPluginReleaseResponse

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration

/**
 * HTTP client for communicating with a Nextflow plugin registry.
 *
 * This client implements the two-step upload process:
 * 1. Create draft release with metadata (id, version, checksum)
 * 2. Upload artifact binary and complete the release
 *
 * This approach enables better validation, error handling, and
 * memory-efficient streaming uploads.
 */
@Slf4j
@CompileStatic
class RegistryClient {
    private final URI url
    private final String authToken
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())

    /**
     * Creates a new registry client.
     * 
     * @param url The base URL of the registry API endpoint
     * @param authToken The bearer token for authentication
     * @throws RegistryReleaseException if authToken is null or empty
     */
    RegistryClient(URI url, String authToken) {
        if (!authToken)
            throw new RegistryReleaseException("API key not specified - Provide a valid API key in 'publishing.registry' configuration")
        this.url = !url.toString().endsWith("/")
            ? URI.create(url.toString() + "/")
            : url
        this.authToken = authToken
    }

    /**
     * Releases a plugin to the registry using the two-step upload process.
     *
     * Step 1: Creates a draft release with metadata (id, version, checksum, provider)
     * Step 2: Uploads the artifact binary and completes the release
     *
     * @param id The plugin identifier/name
     * @param version The plugin version (must be valid semver)
     * @param file The plugin zip file to upload
     * @param provider The plugin provider
     * @throws RegistryReleaseException if the upload fails or returns an error
     */
    def release(String id, String version, File file, String provider) {
        log.info("Releasing plugin ${id}@${version} using two-step upload")

        // Step 1: Create draft release with metadata
        def releaseId = createDraftRelease(id, version, file, provider)
        log.debug("Created draft release with ID: ${releaseId}")

        // Step 2: Upload artifact and complete the release
        uploadArtifact(releaseId, file)
        log.info("Successfully released plugin ${id}@${version}")
    }

    /**
     * Releases a plugin to the registry with duplicate handling using the two-step upload process.
     *
     * Unlike the regular release method, this handles HTTP 409 "DUPLICATE_PLUGIN"
     * errors as non-failing conditions.
     *
     * @param id The plugin identifier/name
     * @param version The plugin version (must be valid semver)
     * @param file The plugin zip file to upload
     * @param provider The plugin provider
     * @return Map with keys: success (boolean), skipped (boolean), message (String)
     * @throws RegistryReleaseException if the upload fails for reasons other than duplicates
     */
    def releaseIfNotExists(String id, String version, File file, String provider) {
        log.info("Releasing plugin ${id}@${version} using two-step upload (if not exists)")

        try {
            // Step 1: Create draft release with metadata
            def releaseId = createDraftRelease(id, version, file, provider)
            log.debug("Created draft release with ID: ${releaseId}")

            // Step 2: Upload artifact and complete the release
            uploadArtifact(releaseId, file)
            log.info("Successfully released plugin ${id}@${version}")

            return [success: true, skipped: false, message: null]
        } catch (RegistryReleaseException e) {
            // Check if it's a duplicate error (409)
            if (e.message?.contains("409")) {
                log.info("Plugin ${id}@${version} already exists, skipping")
                return [success: true, skipped: true, message: e.message]
            }
            // Re-throw for other errors
            throw e
        }
    }

    /**
     * Step 1: Creates a draft release with metadata only.
     *
     * Sends plugin metadata (id, version, checksum, provider) to create a draft release
     * and returns the release ID for use in Step 2.
     *
     * @param id The plugin identifier/name
     * @param version The plugin version
     * @param file The plugin zip file (used to compute checksum)
     * @param provider The plugin provider
     * @return The draft release ID
     * @throws RegistryReleaseException if the request fails
     */
    private Long createDraftRelease(String id, String version, File file, String provider) {
        if (!provider) {
            throw new IllegalArgumentException("Plugin provider is required for plugin upload")
        }

        def client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        // Calculate SHA-512 checksum
        def fileBytes = Files.readAllBytes(file.toPath())
        def checksum = computeSha512(fileBytes)

        // Build request using API model with fluent API
        def request = new CreatePluginReleaseRequest()
            .id(id)
            .version(version)
            .checksum("sha512:${checksum}".toString())
            .provider(provider)

        def jsonBody = objectMapper.writeValueAsString(request)

        def requestUri = URI.create(url.toString() + "v1/plugins/release")
        def httpRequest = HttpRequest.newBuilder()
            .uri(requestUri)
            .header("Authorization", "Bearer ${authToken}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofMinutes(1))
            .build()

        try {
            def response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw new RegistryReleaseException(getErrorMessage(response, requestUri))
            }

            // Parse JSON response using API model
            def responseObj = objectMapper.readValue(response.body(), CreatePluginReleaseResponse)
            return responseObj.getReleaseId()
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new RegistryReleaseException("Plugin draft creation to ${requestUri} was interrupted: ${e.message}", e)
        } catch (ConnectException e) {
            throw new RegistryReleaseException("Unable to connect to plugin repository at ${requestUri}: Connection refused", e)
        } catch (IOException e) {
            throw new RegistryReleaseException("Unable to connect to plugin repository at ${requestUri}: ${e.message}", e)
        }
    }

    /**
     * Step 2: Uploads the artifact binary and completes the release.
     *
     * Uploads the plugin zip file using streaming to complete the draft release
     * and publish it to the registry.
     *
     * @param releaseId The draft release ID from Step 1
     * @param file The plugin zip file to upload
     * @throws RegistryReleaseException if the upload fails
     */
    private void uploadArtifact(Long releaseId, File file) {
        def client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        def boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "")
        def multipartBody = buildArtifactUploadBody(file, boundary)

        def requestUri = URI.create(url.toString() + "v1/plugins/release/${releaseId}/upload")
        def request = HttpRequest.newBuilder()
            .uri(requestUri)
            .header("Authorization", "Bearer ${authToken}")
            .header("Content-Type", "multipart/form-data; boundary=${boundary}")
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
            .timeout(Duration.ofMinutes(5))
            .build()

        try {
            def response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                throw new RegistryReleaseException(getErrorMessage(response, requestUri))
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
            throw new RegistryReleaseException("Plugin artifact upload to ${requestUri} was interrupted: ${e.message}", e)
        } catch (ConnectException e) {
            throw new RegistryReleaseException("Unable to connect to plugin repository at ${requestUri}: Connection refused", e)
        } catch (UnknownHostException | IOException e) {
            throw new RegistryReleaseException("Unable to connect to plugin repository at ${requestUri}: ${e.message}", e)
        }
    }

    private String getErrorMessage(HttpResponse<String> response, URI requestUri) {
        def message = "Failed to release plugin to registry ${requestUri}: HTTP ${response.statusCode()}"
        def body = response.body()
        if (body && !body.isEmpty()) {
            return "$message - $body".toString()
        }
        return message.toString()
    }

    /**
     * Builds multipart body for Step 2 (artifact upload only).
     *
     * @param file The plugin zip file to upload
     * @param boundary The multipart boundary string
     * @return Multipart body as byte array
     */
    private byte[] buildArtifactUploadBody(File file, String boundary) {
        def output = new ByteArrayOutputStream()
        def writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)
        def lineEnd = "\r\n"

        // Read file bytes
        def fileBytes = Files.readAllBytes(file.toPath())

        // Add file field (changed from "artifact" to "payload" per API spec)
        writer.append("--${boundary}").append(lineEnd)
        writer.append("Content-Disposition: form-data; name=\"payload\"; filename=\"${file.name}\"").append(lineEnd)
        writer.append("Content-Type: application/zip").append(lineEnd)
        writer.append(lineEnd)
        writer.flush()

        // Write file bytes
        output.write(fileBytes)

        writer.append(lineEnd)
        writer.append("--${boundary}--").append(lineEnd)
        writer.close()

        return output.toByteArray()
    }

    private String computeSha512(byte[] data) {
        def digest = MessageDigest.getInstance("SHA-512")
        def hash = digest.digest(data)
        return hash.collect { String.format("%02x", it) }.join('')
    }
}
