package io.nextflow.gradle

import groovy.transform.CompileStatic
import io.nextflow.gradle.registry.RegistryReleaseConfig
import org.gradle.api.Project

/**
 * A gradle 'extension' to hold the 'nextflowPlugin.publishing'
 * configuration from build.gradle.
 */
@CompileStatic
class PluginReleaseConfig {
    private final Project project

    /**
     * Configuration for releasing to a registry
     */
    RegistryReleaseConfig registry

    PluginReleaseConfig(Project project) {
        this.project = project
    }

    def validate() {}

    def registry(Closure config) {
        registry = new RegistryReleaseConfig(project)
        project.configure(registry, config)
    }
}
