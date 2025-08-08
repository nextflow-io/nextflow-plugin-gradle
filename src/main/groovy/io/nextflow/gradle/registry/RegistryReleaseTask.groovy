package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task for releasing a Nextflow plugin to a registry.
 * 
 * This task uploads the assembled plugin zip file to a configured registry
 * using the registry's REST API. The plugin zip file is sent as a multipart
 * form request along with the plugin ID and version metadata.
 */
@CompileStatic
class RegistryReleaseTask extends DefaultTask {
    /**
     * The plugin zip file to be uploaded to the registry.
     * By default, this points to the zip file created by the packagePlugin task.
     */
    @InputFile
    final RegularFileProperty zipFile

    RegistryReleaseTask() {
        group = 'Nextflow Plugin'
        description = 'Release the assembled plugin to the registry'

        final buildDir = project.layout.buildDirectory.get()
        zipFile = project.objects.fileProperty()
        zipFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}.zip")
        })
    }

    /**
     * Executes the registry release task.
     * 
     * This method retrieves the plugin configuration and creates a RegistryClient
     * to upload the plugin zip file to the configured registry endpoint.
     * 
     * @throws RegistryReleaseException if the upload fails
     */
    @TaskAction
    def run() {
        final version = project.version.toString()
        final plugin = project.extensions.getByType(NextflowPluginConfig)
        
        // Get or create registry configuration
        def registryConfig
        if (plugin.registry) {
            registryConfig = plugin.registry
        } else {
            // Create default registry config that will use fallback values
            registryConfig = new RegistryReleaseConfig(project)
        }

        def client = new RegistryClient(new URI(registryConfig.resolvedUrl), registryConfig.resolvedAuthToken)
        client.release(project.name, version, project.file(zipFile))
    }
}
