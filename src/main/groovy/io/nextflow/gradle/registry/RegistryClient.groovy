package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

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
        def req = new HttpPost(url.resolve("v1/plugins/release"))
        req.addHeader("Authorization", "Bearer ${authToken}")
        req.setEntity(MultipartEntityBuilder.create()
            .addTextBody("id", id)
            .addTextBody("version", version)
            .addBinaryBody("artifact", file)
            .build())

        try (def http = HttpClients.createDefault();
             def rep = http.execute(req)) {

            if (rep.statusLine.statusCode != 200) {
                throw new RegistryReleaseException(getErrorMessage(rep))
            }
        } catch (ConnectException | UnknownHostException e) {
            throw new RegistryReleaseException("Unable to connect to plugin repository: ${e.message}", e)
        }
    }

    private String getErrorMessage(CloseableHttpResponse rep) {
        def message = "Failed to publish plugin to registry $url: HTTP Response: ${rep.statusLine}"
        if( rep.entity ) {
            final String entityStr = EntityUtils.toString(rep.entity)
            if (entityStr) {
                return "$message - $entityStr".toString()
            }
        }
        return message.toString()
    }
}
