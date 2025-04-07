package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import org.gradle.api.Project

@CompileStatic
class RegistryPublishConfig {
    private final Project project

    /**
     * Location of the registry api
     */
    // replace this with default registry url
    String url = 'http://localhost:8080'

    /**
     * Registry authentication token
     */
    String authToken

    RegistryPublishConfig(Project project) {
        this.project = project
    }
}
