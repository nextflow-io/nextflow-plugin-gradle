package io.nextflow.gradle.registry

import com.github.tomakehurst.wiremock.WireMockServer
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig

class RegistryClientTest extends Specification {

    WireMockServer wireMockServer
    RegistryClient client
    @TempDir
    Path tempDir

    def setup() {
        wireMockServer = new WireMockServer(wireMockConfig().port(0))
        wireMockServer.start()
        def baseUrl = "http://localhost:${wireMockServer.port()}"
        client = new RegistryClient(new URI(baseUrl), "test-token")
    }

    def cleanup() {
        wireMockServer?.stop()
    }

    def "should construct client with URL ending in slash"() {
        when:
        def client1 = new RegistryClient(new URI("http://example.com"), "token")
        def client2 = new RegistryClient(new URI("http://example.com/"), "token")

        then:
        client1.url.toString() == "http://example.com/"
        client2.url.toString() == "http://example.com/"
    }

    def "Should fail when no token provided"(){
        when:
        new RegistryClient(new URI("http://example.com"), null)
        then:
        def ex = thrown(RegistryReleaseException)
        ex.message == "Authentication token not specified - Provide a valid token in 'publishing.registry' configuration"
    }

    def "should successfully publish plugin"() {
        given:
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin content"
        
        wireMockServer.stubFor(post(urlEqualTo("/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(containing("id"))
            .withRequestBody(containing("version"))
            .withRequestBody(containing("checksum"))
            .withRequestBody(containing("artifact"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody('{"status": "success"}')))

        when:
        client.release("test-plugin", "1.0.0", pluginFile)

        then:
        noExceptionThrown()
        
        and:
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token")))
    }

    def "should throw RegistryReleaseException on HTTP error without response body"() {
        given:
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin content"
        
        wireMockServer.stubFor(post(urlEqualTo("/v1/plugins/release"))
            .willReturn(aResponse()
                .withStatus(400)))

        when:
        client.release("test-plugin", "1.0.0", pluginFile)

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.contains("Failed to release plugin to registry http://localhost:")
        ex.message.contains("HTTP 400")
    }

    def "should throw RegistryReleaseException on HTTP error with response body"() {
        given:
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin content"
        
        wireMockServer.stubFor(post(urlEqualTo("/v1/plugins/release"))
            .willReturn(aResponse()
                .withStatus(422)
                .withBody('{"error": "Plugin validation failed"}')))

        when:
        client.release("test-plugin", "1.0.0", pluginFile)

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.contains("Failed to release plugin to registry http://localhost:")
        ex.message.contains("HTTP 422")
        ex.message.contains('{"error": "Plugin validation failed"}')
    }

    def "should fail when connection error"() {
        given:
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin content"
        
        // Stop the server to simulate connection error
        wireMockServer.stop()

        when:
        client.release("test-plugin", "1.0.0", pluginFile)

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.startsWith("Unable to connect to plugin repository at ")
        ex.message.contains("Connection refused")
    }

    def "should fail when unknown host"(){
        given:
        def clientNotfound = new RegistryClient(new URI("http://fake-host.fake-domain-blabla.com"), "token")
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin content"

        when:
        clientNotfound.release("test-plugin", "1.0.0", pluginFile)

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.startsWith('Unable to connect to plugin repository at ')
        // Java HTTP client may convert UnknownHostException to ConnectException
    }

    def "should send correct multipart form data"() {
        given:
        def pluginFile = tempDir.resolve("test-plugin.zip").toFile()
        pluginFile.text = "fake plugin zip content"
        
        wireMockServer.stubFor(post(urlEqualTo("/v1/plugins/release"))
            .willReturn(aResponse().withStatus(200)))

        when:
        client.release("my-plugin", "2.1.0", pluginFile)

        then:
        wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(containing("Content-Disposition: form-data; name=\"id\""))
            .withRequestBody(containing("my-plugin"))
            .withRequestBody(containing("Content-Disposition: form-data; name=\"version\""))
            .withRequestBody(containing("2.1.0"))
            .withRequestBody(containing("Content-Disposition: form-data; name=\"checksum\""))
            .withRequestBody(containing("sha512:35ab27d09f1bc0d4a73b38fbd020064996fb013e2f92d3dd36bda7364765c229e90e0213fcd90c56fc4c9904e259c482cfaacb22dab327050d7d52229eb1a73c"))
            .withRequestBody(containing("Content-Disposition: form-data; name=\"artifact\""))
            .withRequestBody(containing("fake plugin zip content")))
    }
}
