package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils

@Slf4j
@CompileStatic
class RegistryClient {
    private final URI url
    private final String authToken

    RegistryClient(URI url, String authToken) {
        if (!authToken)
            throw new RegistryPublishException("Authentication token not specified - Provide a valid token in 'publishing.registry' configuration")
        this.url = !url.toString().endsWith("/")
            ? URI.create(url.toString() + "/")
            : url
        this.authToken = authToken
    }

    def publish(String id, String version, File file) {
        def req = new HttpPost(url.resolve("v1/plugins/publish"))
        req.addHeader("Authorization", "Bearer ${authToken}")
        req.setEntity(MultipartEntityBuilder.create()
            .addTextBody("id", id)
            .addTextBody("version", version)
            .addBinaryBody("file", file)
            .build())

        try (def http = HttpClients.createDefault();
             def rep = http.execute(req)) {

            if (rep.statusLine.statusCode != 200) {
                throw new RegistryPublishException(getErrorMessage(rep))
            }
        } catch (ConnectException | UnknownHostException e) {
            throw new RegistryPublishException("Unable to connect to plugin repository: ${e.message}", e)
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
