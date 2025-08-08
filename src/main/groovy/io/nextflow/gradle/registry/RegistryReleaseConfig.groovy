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
    String url

    /**
     * The authentication token (bearer token) for registry access.
     * Required for uploading plugins to the registry.
     */
    String authToken

    RegistryReleaseConfig(Project project) {
        this.project = project
    }

    /**
     * Gets the resolved registry URL with fallback to environment variable.
     * @return the resolved URL or default if not configured
     */
    String getResolvedUrl() {
        return url ?: 
               project.findProperty('npr.apiUrl') ?: 
               System.getenv('NPR_API_URL') ?: 
               'https://registry.nextflow.io/api'
    }

    /**
     * Gets the resolved auth token with fallback to environment variable.
     * @return the resolved auth token
     * @throws RuntimeException if no auth token is configured
     */
    String getResolvedAuthToken() {
        def token = authToken ?: 
                    project.findProperty('npr.apiKey') ?: 
                    System.getenv('NPR_API_KEY')
        
        if (!token) {
            throw new RuntimeException('Registry authentication token must be configured either via authToken property, npr.apiKey project property, or NPR_API_KEY environment variable')
        }
        
        return token
    }
}
