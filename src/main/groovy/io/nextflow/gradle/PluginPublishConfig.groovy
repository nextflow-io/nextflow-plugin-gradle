package io.nextflow.gradle

import groovy.transform.CompileStatic
import io.nextflow.gradle.github.GithubPublishConfig
import io.nextflow.gradle.registry.RegistryPublishConfig
import org.gradle.api.Project

/**
 * A gradle 'extension' to hold the 'nextflowPlugin.publishing'
 * configuration from build.gradle.
 */
@CompileStatic
class PluginPublishConfig {
    private final Project project

    /**
     * Configuration for publishing to github
     */
    GithubPublishConfig github

    /**
     * Configuration for publishing to a registry
     */
    RegistryPublishConfig registry

    PluginPublishConfig(Project project) {
        this.project = project
    }

    def validate() {}

    // initialises the 'github' sub-config
    def github(Closure config) {
        github = new GithubPublishConfig(project)
        project.configure(github, config)
    }

    def registry(Closure config) {
        registry = new RegistryPublishConfig(project)
        project.configure(registry, config)
    }
}
