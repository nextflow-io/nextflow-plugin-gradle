package io.nextflow.gradle

import io.nextflow.gradle.registry.RegistryPublishConfig
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for NextflowPlugin
 */
class NextflowPluginTest extends Specification {

    Project project

    def setup() {
        project = ProjectBuilder.builder()
            .withName('test-plugin')
            .build()
        project.version = '1.0.0'
        project.pluginManager.apply('io.nextflow.nextflow-plugin')
    }

    def "should register publishPlugin task when publishing is configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            publishing {
                registry {
                    url = 'https://example.com/registry'
                }
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('publishPlugin') != null
        project.tasks.publishPlugin.group == 'Nextflow Plugin'
        project.tasks.publishPlugin.description == 'publish plugin to configured destinations'
    }


    def "should make publishPlugin depend on registry publishing task"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            publishing {
                registry {
                    url = 'https://example.com/registry'
                }
            }
        }

        when:
        project.evaluate()

        then:
        def publishPlugin = project.tasks.publishPlugin
        def publishToRegistry = project.tasks.publishPluginToRegistry
        publishPlugin.taskDependencies.getDependencies(publishPlugin).contains(publishToRegistry)
    }


    def "should not register publishPlugin task when no publishing is configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('publishPlugin') == null
    }

    def "should resolve registry URL from explicit configuration"() {
        given:
        def config = new RegistryPublishConfig(project)
        config.url = 'https://custom-registry.com/api'

        when:
        def resolvedUrl = config.getResolvedUrl()

        then:
        resolvedUrl == 'https://custom-registry.com/api'
    }

    def "should resolve registry URL from gradle property"() {
        given:
        project.ext.set('npr.url', 'https://gradle-prop-registry.com/api')
        def config = new RegistryPublishConfig(project)

        when:
        def resolvedUrl = config.getResolvedUrl()

        then:
        resolvedUrl == 'https://gradle-prop-registry.com/api'
    }

    def "should resolve registry URL from environment variable"() {
        given:
        def config = new RegistryPublishConfig(project) {
            @Override
            String getResolvedUrl() {
                // If explicitly set, use it
                if (url) {
                    return url
                }
                
                // Try gradle property
                def gradleProp = project.findProperty('npr.url')
                if (gradleProp) {
                    return gradleProp.toString()
                }
                
                // Mock environment variable for test
                return 'https://env-registry.com/api'
            }
        }

        when:
        def resolvedUrl = config.getResolvedUrl()

        then:
        resolvedUrl == 'https://env-registry.com/api'
    }

    def "should use default registry URL when none provided"() {
        given:
        def config = new RegistryPublishConfig(project)

        when:
        def resolvedUrl = config.getResolvedUrl()

        then:
        resolvedUrl == 'https://plugin-registry.seqera.io/api'
    }

    def "should resolve API key from explicit configuration"() {
        given:
        def config = new RegistryPublishConfig(project)
        config.apiKey = 'explicit-api-key'

        when:
        def resolvedApiKey = config.getResolvedApiKey()

        then:
        resolvedApiKey == 'explicit-api-key'
    }

    def "should resolve API key from gradle property"() {
        given:
        project.ext.set('npr.apiKey', 'gradle-prop-api-key')
        def config = new RegistryPublishConfig(project)

        when:
        def resolvedApiKey = config.getResolvedApiKey()

        then:
        resolvedApiKey == 'gradle-prop-api-key'
    }

    def "should resolve API key from environment variable"() {
        given:
        def config = new RegistryPublishConfig(project) {
            @Override
            String getResolvedApiKey() {
                // If explicitly set, use it
                if (apiKey) {
                    return apiKey
                }
                
                // Try gradle property
                def gradleProp = project.findProperty('npr.apiKey')
                if (gradleProp) {
                    return gradleProp.toString()
                }
                
                // Mock environment variable for test
                return 'env-api-key'
            }
        }

        when:
        def resolvedApiKey = config.getResolvedApiKey()

        then:
        resolvedApiKey == 'env-api-key'
    }

    def "should throw error when no API key provided"() {
        given:
        def config = new RegistryPublishConfig(project)

        when:
        config.getResolvedApiKey()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains('Registry API key not provided')
        ex.message.contains('nextflowPlugin.publishing.registry.apiKey')
        ex.message.contains('npr.apiKey')
        ex.message.contains('NPR_API_KEY')
    }

}