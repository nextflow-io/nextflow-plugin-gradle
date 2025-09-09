package io.nextflow.gradle

import com.github.zafarkhaja.semver.Version
import groovy.transform.CompileStatic
import io.nextflow.gradle.registry.RegistryReleaseConfig
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
 *     useDefaultDependencies = false  // optional, defaults to true
 *     extensionPoints = [
 *         'com.example.ExampleFunctions'
 *     ]
 *     registry {
 *         url = 'https://registry.nextflow.io/api'
 *         apiKey = 'my-api-key'
 *     }
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
     * Whether to automatically add default dependencies (default: true)
     */
    boolean useDefaultDependencies = true

    /**
     * Configure registry publishing settings (optional)
     */
    RegistryReleaseConfig registry

    NextflowPluginConfig(Project project) {
        this.project = project
    }

    private String normalizeVersion(String version) {
        try {
            // Normalize Nextflow version format (e.g., "24.04.0" -> "24.4.0") to comply with semver
            def parts = version.split(/\.|-/, 3)
            if (parts.length >= 3) {
                def major = Integer.parseInt(parts[0])
                def minor = Integer.parseInt(parts[1])
                def patch = parts[2]
                def patchParts = patch.split(/-/, 2)
                def patchNumber = Integer.parseInt(patchParts[0])
                def normalized = "${major}.${minor}.${patchNumber}"
                if (patchParts.length > 1) {
                    normalized += "-${patchParts[1]}"
                }
                return normalized
            }
        } catch (NumberFormatException e) {
            // If we can't parse the version, return as-is to let semver validation handle it
        }
        return version
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
        if (provider && !provider.trim()) {
            throw new RuntimeException('nextflowPlugin.provider cannot be empty')
        }

        // validate nextflowVersion is valid semver (normalize to handle Nextflow's version format)
        def normalizedNextflowVersion = normalizeVersion(nextflowVersion)
        if (!Version.isValid(normalizedNextflowVersion, true)) {
            throw new RuntimeException("nextflowPlugin.nextflowVersion '${nextflowVersion}' is invalid. Must be a valid semantic version (semver) string")
        }

        // validate className is valid Java fully qualified class name (must have package)
        if (!className.matches(/^[a-zA-Z_$][a-zA-Z0-9_$]*(\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$/)) {
            throw new RuntimeException("nextflowPlugin.className '${className}' is invalid. Must be a valid Java fully qualified class name with package")
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

    // initialises the 'registry' sub-config
    def registry(Closure config) {
        registry = new RegistryReleaseConfig(project)
        project.configure(registry, config)
    }
}
