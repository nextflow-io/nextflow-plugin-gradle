package io.nextflow.gradle

import com.github.zafarkhaja.semver.Version
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
     * Description of this plugin.
     * (optional)
     */
    String description

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

    /**
     * Configure how the plugin will be released (optional)
     */
    PluginReleaseConfig publishing

    NextflowPluginConfig(Project project) {
        this.project = project
    }

    def validate() {
        // check for missing config
        if (!nextflowVersion) {
            throw new RuntimeException('nextflowPlugin.nextflowVersion not specified')
        }
        if (!className) {
            throw new RuntimeException('nextflowPlugin.className not specified')
        }
        if (!provider) {
            throw new RuntimeException('nextflowPlugin.provider not specified')
        }

        // validate name/id
        if (!project.name.toString().matches(/[a-zA-Z0-9-]{5,64}/)) {
            throw new RuntimeException("Plugin id '${project.name}' is invalid. Plugin ids can contain numbers, letters, and the '-' symbol")
        }
        // validate version is valid semver
        if (!Version.isValid(project.version.toString(), true)) {
            throw new RuntimeException("Plugin version '${project.version}' is invalid. Plugin versions must be a valid semantic version (semver) string")
        }
    }

    // initialises the 'publishing' sub-config
    def publishing(Closure config) {
        publishing = new PluginReleaseConfig(project)
        project.configure(publishing, config)
        publishing.validate()
    }
}
