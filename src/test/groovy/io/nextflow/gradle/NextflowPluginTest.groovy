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
            publishing {
                registry {
                    url = 'https://example.com/registry'
                }
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
            publishing {
                registry {
                    url = 'https://example.com/registry'
                }
            }
        }

        when:
        project.evaluate()

        then:
        def releasePlugin = project.tasks.releasePlugin
        def releaseToRegistry = project.tasks.releasePluginToRegistry
        releasePlugin.taskDependencies.getDependencies(releasePlugin).contains(releaseToRegistry)
    }


    def "should not register releasePlugin task when no publishing is configured"() {
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
        project.tasks.findByName('releasePlugin') == null
    }

}
