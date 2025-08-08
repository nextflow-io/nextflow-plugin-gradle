package io.nextflow.gradle.registry

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for RegistryReleaseConfig fallback functionality
 */
class RegistryReleaseConfigTest extends Specification {

    Project project
    RegistryReleaseConfig config

    def setup() {
        project = ProjectBuilder.builder()
            .withName('test-plugin')
            .build()
        config = new RegistryReleaseConfig(project)
    }


    def "should use default URL when no configuration provided"() {
        when:
        def resolvedUrl = config.resolvedUrl

        then:
        resolvedUrl == 'https://registry.nextflow.io/api'
    }

    def "should use explicit URL configuration over defaults"() {
        given:
        config.url = 'https://custom-registry.com/api'

        when:
        def resolvedUrl = config.resolvedUrl

        then:
        resolvedUrl == 'https://custom-registry.com/api'
    }

    def "should use project property for URL when not explicitly set"() {
        given:
        project.ext['npr.apiUrl'] = 'https://project-property-registry.com/api'

        when:
        def resolvedUrl = config.resolvedUrl

        then:
        resolvedUrl == 'https://project-property-registry.com/api'
    }

    def "should prioritize explicit URL over project property"() {
        given:
        config.url = 'https://explicit-registry.com/api'
        project.ext['npr.apiUrl'] = 'https://project-registry.com/api'

        when:
        def resolvedUrl = config.resolvedUrl

        then:
        resolvedUrl == 'https://explicit-registry.com/api'
    }

    def "should use explicit auth token configuration"() {
        given:
        config.authToken = 'explicit-token'

        when:
        def resolvedToken = config.resolvedAuthToken

        then:
        resolvedToken == 'explicit-token'
    }

    def "should use project property for auth token when not explicitly set"() {
        given:
        project.ext['npr.apiKey'] = 'project-property-token'

        when:
        def resolvedToken = config.resolvedAuthToken

        then:
        resolvedToken == 'project-property-token'
    }

    def "should prioritize explicit auth token over project property"() {
        given:
        config.authToken = 'explicit-token'
        project.ext['npr.apiKey'] = 'project-token'

        when:
        def resolvedToken = config.resolvedAuthToken

        then:
        resolvedToken == 'explicit-token'
    }

    def "should throw exception when no auth token is configured"() {
        when:
        config.resolvedAuthToken

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains('Registry authentication token must be configured')
        ex.message.contains('npr.apiKey project property')
        ex.message.contains('NPR_API_KEY environment variable')
    }
}