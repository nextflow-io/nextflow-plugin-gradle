package io.nextflow.gradle.registry

import com.google.gson.Gson
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
    private final Gson gson = new Gson()

    private final URI url
    private final String apiKey

    RegistryClient(URI url, String apiKey) {
        this.url = !url.toString().endsWith("/")
            ? URI.create(url.toString() + "/")
            : url
        this.apiKey = apiKey
    }

    def publish(String id, String version, File file) {
        def req = new HttpPost(url.resolve("publish"))
        req.addHeader("Authorization", "Bearer ${apiKey}")
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
        } catch (ConnectException e) {
            throw new RuntimeException("Unable to connect to plugin repository: (${e.message})")
        }
    }

    private String getErrorMessage(CloseableHttpResponse rep) {
        def message = "Failed to publish plugin to registry $url: HTTP Response:${rep.statusLine}"
        if( rep.entity ) {
            final String entityStr = EntityUtils.toString(rep.entity)
            if (entityStr) {
                return "$message - $entityStr".toString()
            }
        }
        return message.toString()
    }
}
