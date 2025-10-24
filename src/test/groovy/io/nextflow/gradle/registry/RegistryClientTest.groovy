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
        def baseUrl = "http://localhost:${wireMockServer.port()}/api"
        client = new RegistryClient(new URI(baseUrl), "test-token")
    }

    def cleanup() {
        wireMockServer?.stop()
    }

    def createPluginArchive() {
        def result = tempDir.resolve("test-plugin.zip").toFile()
        result.text = "fake plugin zip content"
        return result
    }

    def createPluginSpec() {
        def result = tempDir.resolve("spec.json").toFile()
        result.text = "fake plugin spec content"
        return result
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
        ex.message == "API key not specified - Provide a valid API key in 'publishing.registry' configuration"
    }

    def "should successfully publish plugin using two-step process"() {
        given:
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()

        // Step 1: Create draft release (JSON)
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(containing("\"id\""))
            .withRequestBody(containing("\"version\""))
            .withRequestBody(containing("\"checksum\""))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody('{"releaseId": 123, "pluginRelease": {"version": "1.0.0", "url": "https://example.com/plugin.zip", "date": "2024-01-01T00:00:00Z", "sha512sum": "abc123", "requires": ">=21.0.0", "dependsOn": [], "downloadCount": 0, "downloadGhCount": 0}}')))

        // Step 2: Upload artifact (multipart)
        wireMockServer.stubFor(post(urlMatching("/api/v1/plugins/release/.*/upload"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(containing("payload"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody('{"pluginRelease": {"version": "1.0.0", "url": "https://example.com/plugin.zip", "date": "2024-01-01T00:00:00Z", "sha512sum": "abc123", "requires": ">=21.0.0", "dependsOn": [], "downloadCount": 0, "downloadGhCount": 0}}')))

        when:
        client.release("test-plugin", "1.0.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        noExceptionThrown()

        and:
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("Content-Type", equalTo("application/json")))
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/plugins/release/123/upload"))
            .withHeader("Authorization", equalTo("Bearer test-token")))
    }

    def "should throw RegistryReleaseException on HTTP error in draft creation without response body"() {
        given:
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/plugins/release"))
            .willReturn(aResponse()
                .withStatus(400)))

        when:
        client.release("test-plugin", "1.0.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.contains("Failed to release plugin to registry http://localhost:")
        ex.message.contains("HTTP 400")
    }

    def "should throw RegistryReleaseException on HTTP error in draft creation with response body"() {
        given:
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/plugins/release"))
            .willReturn(aResponse()
                .withStatus(422)
                .withBody('{"error": "Plugin validation failed"}')))

        when:
        client.release("test-plugin", "1.0.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.contains("Failed to release plugin to registry http://localhost:")
        ex.message.contains("HTTP 422")
        ex.message.contains('{"error": "Plugin validation failed"}')
    }

    def "should fail when connection error"() {
        given:
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()
        
        // Stop the server to simulate connection error
        wireMockServer.stop()

        when:
        client.release("test-plugin", "1.0.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.startsWith("Unable to connect to plugin repository at ")
        ex.message.contains("Connection refused")
    }

    def "should fail when unknown host"(){
        given:
        def clientNotfound = new RegistryClient(new URI("http://fake-host.fake-domain-blabla.com"), "token")
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()

        when:
        clientNotfound.release("test-plugin", "1.0.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        def ex = thrown(RegistryReleaseException)
        ex.message.startsWith('Unable to connect to plugin repository at ')
        // Java HTTP client may convert UnknownHostException to ConnectException
    }

    def "should send correct JSON in two-step process"() {
        given:
        def pluginArchive = createPluginArchive()
        def pluginSpec = createPluginSpec()

        // Step 1: Create draft with metadata (JSON)
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/plugins/release"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody('{"releaseId": 456, "pluginRelease": {"version": "2.1.0", "url": "https://example.com/plugin.zip", "date": "2024-01-01T00:00:00Z", "sha512sum": "abc123", "requires": ">=21.0.0", "dependsOn": [], "downloadCount": 0, "downloadGhCount": 0}}')))

        // Step 2: Upload artifact (multipart)
        wireMockServer.stubFor(post(urlMatching("/api/v1/plugins/release/.*/upload"))
            .willReturn(aResponse().withStatus(200)))

        when:
        client.release("my-plugin", "2.1.0", pluginSpec, pluginArchive, "seqera.io")

        then:
        // Verify Step 1: draft creation with JSON metadata
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/plugins/release"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(containing("\"id\":\"my-plugin\""))
            .withRequestBody(containing("\"version\":\"2.1.0\""))
            .withRequestBody(containing("\"checksum\":\"sha512:35ab27d09f1bc0d4a73b38fbd020064996fb013e2f92d3dd36bda7364765c229e90e0213fcd90c56fc4c9904e259c482cfaacb22dab327050d7d52229eb1a73c\""))
            .withRequestBody(containing("\"spec\":\"fake plugin spec content\""))
            .withRequestBody(containing("\"provider\":\"seqera.io\"")))

        // Verify Step 2: artifact upload with multipart form data
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/v1/plugins/release/456/upload"))
            .withHeader("Authorization", equalTo("Bearer test-token"))
            .withRequestBody(containing("Content-Disposition: form-data; name=\"payload\""))
            .withRequestBody(containing("fake plugin zip content")))
    }
}
