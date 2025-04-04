package io.nextflow.gradle

import groovy.transform.CompileStatic
import io.nextflow.gradle.github.GithubPublishConfig
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

    PluginPublishConfig(Project project) {
        this.project = project
    }

    def validate() {}

    // initialises the 'github' sub-config
    def github(Closure config) {
        github = new GithubPublishConfig(project)
        project.configure(github, config)
    }

    // get the url of the published plugin
    def url() {
        github.publishedUrl()
    }
}
