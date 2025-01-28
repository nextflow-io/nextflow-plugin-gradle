package io.nextflow.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Project

/**
 * A gradle 'extension' to provide configuration to the
 * Nextflow gradle plugin.
 *
 * Usage in 'build.gradle':
 * <pre>
 * nextflowPlugin {
 *     nextflowVersion = '25.04.0'
 *     publisher = 'nextflow'
 *     className = 'com.example.ExamplePlugin'
 *     extensionPoints = [
 *         'com.example.ExampleFunctions'
 *     ]
 * }
 * </pre>
 */
@CompileStatic
class NextflowPluginConfig {
    private final Project project

    /**
     * Minimum required nextflow version
     */
    String nextflowVersion = '24.11.0-edge'

    /**
     * Who created/maintains this plugin?
     */
    String provider

    /**
     * What class should be created when the plugin is loaded?
     */
    String className

    /**
     * Does this plugin require any other plugins to function?
     * (optional)
     */
    List<String> requirePlugins = []

    /**
     * List of extension points provided by the plugin.
     */
    List<String> extensionPoints = []

    NextflowPluginConfig(Project project) {
        this.project = project
    }

    def validate() {
        if (nextflowVersion)

        if (!className) {
            throw new RuntimeException('nextflowPlugin.className not specified')
        }
        if (!provider) {
            throw new RuntimeException('nextflowPlugin.provider not specified')
        }
    }
}
