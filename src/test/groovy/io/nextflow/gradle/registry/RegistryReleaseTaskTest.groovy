package io.nextflow.gradle.registry

import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Unit tests for RegistryReleaseTask error handling
 */
class RegistryReleaseTaskTest extends Specification {

    Project project
    RegistryReleaseTask task
    @TempDir
    Path tempDir

    def setup() {
        project = ProjectBuilder.builder()
            .withName('test-plugin')
            .build()
        project.version = '1.0.0'
        
        // Apply the plugin
        project.pluginManager.apply('io.nextflow.nextflow-plugin')
        
        // Create the task
        task = project.tasks.create('testReleaseTask', RegistryReleaseTask)
        
        // Set up a test zip file
        def testZip = tempDir.resolve("test-plugin-1.0.0.zip").toFile()
        testZip.text = "fake plugin content"
        task.zipFile.set(testZip)
    }

    def "should use default fallback configuration when registry is not configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            // No registry configuration - will use fallback
        }

        when:
        task.run()

        then:
        // Should fail with token configuration error since no fallback token is available
        def ex = thrown(RuntimeException)
        ex.message.contains('Registry authentication token must be configured')
    }

    def "should use default fallback configuration when empty registry block is provided"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                // Empty registry block - will use fallback
            }
        }

        when:
        task.run()

        then:
        // Should fail with token configuration error since no fallback token is available
        def ex = thrown(RuntimeException)
        ex.message.contains('Registry authentication token must be configured')
    }

    def "should throw exception when auth token is not configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                url = 'https://example.com/registry'
                // No auth token
            }
        }

        when:
        task.run()

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains('Registry authentication token must be configured')
    }

    def "should use fallback configuration when explicit values not set"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                url = 'https://example.com/registry'
                authToken = 'test-token'
            }
        }

        when:
        task.run()

        then:
        // Should fail with connection error, not configuration error
        thrown(RegistryReleaseException)
    }

    def "should work with project property fallback for auth token with explicit registry"() {
        given:
        project.ext['npr.apiKey'] = 'project-token'
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                url = 'https://example.com/registry'
                // No explicit auth token - will fall back to project property
            }
        }

        when:
        task.run()

        then:
        // Should fail with connection error, not configuration error
        thrown(RegistryReleaseException)
    }

    def "should work with project property fallbacks when no registry block exists"() {
        given:
        project.ext['npr.apiKey'] = 'project-token'
        project.ext['npr.apiUrl'] = 'https://project-registry.com/api'
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            // No registry block at all - should use project property fallbacks
        }

        when:
        task.run()

        then:
        // Should fail with connection error, not configuration error
        // This proves the fallback configuration is working
        thrown(RegistryReleaseException)
    }
}