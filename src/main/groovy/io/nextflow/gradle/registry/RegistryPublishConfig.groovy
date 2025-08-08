package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import org.gradle.api.Project

@CompileStatic
class RegistryPublishConfig {
    private final Project project

    /**
     * Location of the registry api
     */
    String url = 'https://registry.nextflow.io/api'

    /**
     * Registry authentication token
     */
    String authToken

    RegistryPublishConfig(Project project) {
        this.project = project
    }
}
