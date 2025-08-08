package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Duration

/**
 * HTTP client for communicating with a Nextflow plugin registry.
 * 
 * This client handles authentication and multipart form uploads
 * to release plugins to a registry service via REST API.
 */
@Slf4j
@CompileStatic
class RegistryClient {
    private final URI url
    private final String authToken

    /**
     * Creates a new registry client.
     * 
     * @param url The base URL of the registry API endpoint
     * @param authToken The bearer token for authentication
     * @throws RegistryReleaseException if authToken is null or empty
     */
    RegistryClient(URI url, String authToken) {
        if (!authToken)
            throw new RegistryReleaseException("Authentication token not specified - Provide a valid token in 'publishing.registry' configuration")
        this.url = !url.toString().endsWith("/")
            ? URI.create(url.toString() + "/")
            : url
        this.authToken = authToken
    }

    /**
     * Releases a plugin to the registry.
     * 
     * Uploads the plugin zip file along with metadata to the registry
     * using a multipart HTTP POST request to the v1/plugins/release endpoint.
     * 
     * @param id The plugin identifier/name
     * @param version The plugin version (must be valid semver)
     * @param file The plugin zip file to upload
     * @throws RegistryReleaseException if the upload fails or returns an error
     */
    def release(String id, String version, File file) {
        def client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

        def boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "")
        def multipartBody = buildMultipartBody(id, version, file, boundary)

        def requestUri = url.resolve("v1/plugins/release")
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
            throw new RegistryReleaseException("Plugin release to ${requestUri} was interrupted: ${e.message}", e)
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

    private byte[] buildMultipartBody(String id, String version, File file, String boundary) {
        def output = new ByteArrayOutputStream()
        def writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)
        def lineEnd = "\r\n"
        
        // Add id field
        writer.append("--${boundary}").append(lineEnd)
        writer.append("Content-Disposition: form-data; name=\"id\"").append(lineEnd)
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd)
        writer.append(lineEnd)
        writer.append(id).append(lineEnd)
        
        // Add version field
        writer.append("--${boundary}").append(lineEnd)
        writer.append("Content-Disposition: form-data; name=\"version\"").append(lineEnd)
        writer.append("Content-Type: text/plain; charset=UTF-8").append(lineEnd)
        writer.append(lineEnd)
        writer.append(version).append(lineEnd)
        
        // Add file field
        writer.append("--${boundary}").append(lineEnd)
        writer.append("Content-Disposition: form-data; name=\"artifact\"; filename=\"${file.name}\"").append(lineEnd)
        writer.append("Content-Type: application/zip").append(lineEnd)
        writer.append(lineEnd)
        writer.flush()
        
        // Write file bytes
        output.write(Files.readAllBytes(file.toPath()))
        
        writer.append(lineEnd)
        writer.append("--${boundary}--").append(lineEnd)
        writer.close()
        
        return output.toByteArray()
    }
}
