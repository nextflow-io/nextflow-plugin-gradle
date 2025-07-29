package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import org.gradle.api.Project

@CompileStatic
class RegistryPublishConfig {
    private final Project project

    /**
     * Location of the registry api
     */
    String url

    /**
     * Registry API key
     */
    String apiKey

    RegistryPublishConfig(Project project) {
        this.project = project
    }

    /**
     * Get the registry URL, checking fallbacks if not explicitly set
     */
    String getResolvedUrl() {
        // If explicitly set, use it
        if (url) {
            return url
        }
        
        // Try gradle property
        def gradleProp = project.findProperty('npr.url')
        if (gradleProp) {
            return gradleProp.toString()
        }
        
        // Try environment variable
        def envVar = System.getenv('NPR_URL')
        if (envVar) {
            return envVar
        }
        
        // Default URL
        return 'https://plugin-registry.seqera.io/api'
    }

    /**
     * Get the API key, checking fallbacks if not explicitly set
     */
    String getResolvedApiKey() {
        // If explicitly set, use it
        if (apiKey) {
            return apiKey
        }
        
        // Try gradle property
        def gradleProp = project.findProperty('npr.apiKey')
        if (gradleProp) {
            return gradleProp.toString()
        }
        
        // Try environment variable
        def envVar = System.getenv('NPR_API_KEY')
        if (envVar) {
            return envVar
        }
        
        // No API key found
        throw new RuntimeException('Registry API key not provided. Set it via:\n' +
            '  - nextflowPlugin.publishing.registry.apiKey in build.gradle\n' +
            '  - gradle property: npr.apiKey\n' +
            '  - environment variable: NPR_API_KEY')
    }
}
