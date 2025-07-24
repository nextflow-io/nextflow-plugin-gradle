package io.nextflow.gradle.registry

import com.google.gson.Gson
import com.google.gson.JsonParseException
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients

@Slf4j
@CompileStatic
class RegistryClient {
    private final Gson gson = new Gson()

    private final URI url
    private final String authToken

    RegistryClient(URI url, String authToken) {
        this.url = !url.toString().endsWith("/")
            ? URI.create(url.toString() + "/")
            : url
        this.authToken = authToken
    }

    def publish(String id, String version, File file) {
        def req = new HttpPost(url.resolve("publish"))
        req.addHeader("Authorization", "Bearer ${authToken}")
        req.setEntity(MultipartEntityBuilder.create()
            .addTextBody("id", id)
            .addTextBody("version", version)
            .addBinaryBody("file", file)
            .build())

        try (def http = HttpClients.createDefault();
             def rep = http.execute(req)) {

            if (rep.statusLine.statusCode != 200) {
                def message = "Failed to publish plugin to registry $url: HTTP Response:${rep.statusLine}"
                try{
                    def err = gson.fromJson(new InputStreamReader(rep.entity.content), ErrorResponse)
                    message << " - Error type: ${err?.type}, message: ${err?.message}"
                } catch (JsonParseException e){
                    log.debug("Exception parsing error response: $e.message")
                }
                throw new RuntimeException(message)
            }
        } catch (ConnectException e) {
            throw new RuntimeException("Unable to connect to plugin repository: (${e.message})")
        }
    }

    // ----------------------------------------------------------------------------

    private static class ErrorResponse {
        String type
        String message
    }
}
