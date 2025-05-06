package io.nextflow.gradle.registry

import com.google.gson.Gson
import groovy.transform.CompileStatic
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients

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
                def err = gson.fromJson(new InputStreamReader(rep.entity.content), ErrorResponse)
                throw new RuntimeException("Failed to publish plugin: ${err.type} - ${err.message}")
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
