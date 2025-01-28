package io.nextflow.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A gradle plugin for nextflow plugin projects.
 */
class NextflowPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        final config = project.extensions.create('nextflowPlugin', NextflowPluginConfig)
    }
}
