package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import org.gradle.api.Project

/**
 * Configuration for releasing plugins to a registry.
 * 
 * This class holds the registry URL and authentication token
 * needed to upload plugins to a registry service.
 */
@CompileStatic
class RegistryReleaseConfig {
    private final Project project

    /**
     * The registry API base URL.
     * Defaults to the official Nextflow registry.
     */
    String url = 'https://registry.nextflow.io/api'

    /**
     * The authentication token (bearer token) for registry access.
     * Required for uploading plugins to the registry.
     */
    String authToken

    RegistryReleaseConfig(Project project) {
        this.project = project
    }
}
