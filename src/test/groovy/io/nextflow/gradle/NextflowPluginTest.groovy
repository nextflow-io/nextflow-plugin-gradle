package io.nextflow.gradle

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

    def "should register releasePlugin task when publishing is configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                url = 'https://example.com/registry'
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('releasePlugin') != null
        project.tasks.releasePlugin.group == 'Nextflow Plugin'
        project.tasks.releasePlugin.description == 'Release plugin to configured destination'
    }


    def "should make releasePlugin depend on registry publishing task"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                url = 'https://example.com/registry'
            }
        }

        when:
        project.evaluate()

        then:
        def releasePlugin = project.tasks.releasePlugin
        def releaseToRegistry = project.tasks.releasePluginToRegistry
        releasePlugin.taskDependencies.getDependencies(releasePlugin).contains(releaseToRegistry)
    }


    def "should always register releasePlugin tasks even when no registry is configured"() {
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
        project.tasks.findByName('releasePlugin') != null
        project.tasks.findByName('releasePluginToRegistry') != null
        project.tasks.releasePlugin.group == 'Nextflow Plugin'
        project.tasks.releasePlugin.description == 'Release plugin to configured destination'
    }

    def "should register releasePlugin tasks when no explicit registry configuration exists"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            // No registry block - should use fallback configuration
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('releasePlugin') != null
        project.tasks.findByName('releasePluginToRegistry') != null
    }

    def "should create releasePlugin task that works with fallback configuration"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            // No registry block - should use fallback configuration
        }

        when:
        project.evaluate()
        def task = project.tasks.findByName('releasePlugin')

        then:
        task != null
        task.group == 'Nextflow Plugin'
        
        and: "Other tasks should also exist"
        project.tasks.findByName('packagePlugin') != null
        project.tasks.findByName('installPlugin') != null
        project.tasks.findByName('releasePluginToRegistry') != null
    }

    def "should handle empty registry block and use fallback configuration"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            registry {
                // completely empty registry block
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('releasePlugin') != null
        project.tasks.findByName('releasePluginToRegistry') != null
        
        and: "Other tasks should also exist"
        project.tasks.findByName('packagePlugin') != null
        project.tasks.findByName('installPlugin') != null
    }

}
