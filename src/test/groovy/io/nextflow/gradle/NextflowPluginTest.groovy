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

    def "should register publishPlugin task when GitHub publishing is configured"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            publishing {
                github {
                    repository = 'test-owner/test-repo'
                    authToken = 'test-token'
                }
            }
        }

        when:
        project.evaluate()

        then:
        project.tasks.findByName('publishPlugin') != null
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

    def "should make publishPlugin depend on GitHub publishing tasks"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            extensionPoints = ['com.example.TestExtension']
            publishing {
                github {
                    repository = 'test-owner/test-repo'
                    authToken = 'test-token'
                    updateIndex = true
                }
            }
        }

        when:
        project.evaluate()

        then:
        def publishPlugin = project.tasks.publishPlugin
        def publishToGithub = project.tasks.publishPluginToGithub
        def updateIndex = project.tasks.updateGithubIndex
        def dependencies = publishPlugin.taskDependencies.getDependencies(publishPlugin)
        dependencies.contains(publishToGithub)
        dependencies.contains(updateIndex)
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

    def "should register publishPlugin with both registry and GitHub publishing"() {
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
                github {
                    repository = 'test-owner/test-repo'
                    authToken = 'test-token'
                }
            }
        }

        when:
        project.evaluate()

        then:
        def publishPlugin = project.tasks.publishPlugin
        def dependencies = publishPlugin.taskDependencies.getDependencies(publishPlugin)
        dependencies.contains(project.tasks.publishPluginToRegistry)
        dependencies.contains(project.tasks.publishPluginToGithub)
    }
}