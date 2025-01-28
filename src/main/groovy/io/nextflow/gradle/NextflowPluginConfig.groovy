package io.nextflow.gradle

import groovy.transform.CompileStatic
import org.gradle.api.Project

/**
 * A gradle 'extension' to provide configuration to the
 * Nextflow gradle plugin.
 */
@CompileStatic
class NextflowPluginConfig {
    private final Project project

    NextflowPluginConfig(Project project) {
        this.project = project
    }
}
