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

    def "should add default dependencies when useDefaultDependencies is true"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            useDefaultDependencies = true
            extensionPoints = ['com.example.TestExtension']
        }

        when:
        project.evaluate()

        then:
        def compileOnlyDeps = project.configurations.compileOnly.dependencies
        compileOnlyDeps.find { it.group == 'io.nextflow' && it.name == 'nextflow' && it.version == '24.04.0' }
        compileOnlyDeps.find { it.group == 'org.slf4j' && it.name == 'slf4j-api' && it.version == '1.7.10' }
        compileOnlyDeps.find { it.group == 'org.pf4j' && it.name == 'pf4j' && it.version == '3.4.1' }

        and: "test dependencies should be added"
        def testDeps = project.configurations.testImplementation.dependencies
        testDeps.find { it.group == 'org.apache.groovy' && it.name == 'groovy' }
        testDeps.find { it.group == 'io.nextflow' && it.name == 'nextflow' && it.version == '24.04.0' }
        testDeps.find { it.group == 'org.spockframework' && it.name == 'spock-core' }
    }

    def "should not add default dependencies when useDefaultDependencies is false"() {
        given:
        project.nextflowPlugin {
            description = 'A test plugin'
            provider = 'Test Author'
            className = 'com.example.TestPlugin'
            nextflowVersion = '24.04.0'
            useDefaultDependencies = false
            extensionPoints = ['com.example.TestExtension']
        }

        when:
        project.evaluate()

        then:
        def compileOnlyDeps = project.configurations.compileOnly.dependencies
        !compileOnlyDeps.find { it.group == 'io.nextflow' && it.name == 'nextflow' }
        !compileOnlyDeps.find { it.group == 'org.slf4j' && it.name == 'slf4j-api' }
        !compileOnlyDeps.find { it.group == 'org.pf4j' && it.name == 'pf4j' }

        and: "test dependencies should not be added"
        def testDeps = project.configurations.testImplementation.dependencies
        !testDeps.find { it.group == 'org.apache.groovy' && it.name == 'groovy' }
        !testDeps.find { it.group == 'io.nextflow' && it.name == 'nextflow' }
        !testDeps.find { it.group == 'org.spockframework' && it.name == 'spock-core' }
    }

    def "should add default dependencies by default when useDefaultDependencies is not specified"() {
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
        def compileOnlyDeps = project.configurations.compileOnly.dependencies
        compileOnlyDeps.find { it.group == 'io.nextflow' && it.name == 'nextflow' && it.version == '24.04.0' }
        compileOnlyDeps.find { it.group == 'org.slf4j' && it.name == 'slf4j-api' && it.version == '1.7.10' }
        compileOnlyDeps.find { it.group == 'org.pf4j' && it.name == 'pf4j' && it.version == '3.4.1' }
    }

}
