package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task for releasing a Nextflow plugin to a registry with duplicate handling.
 * 
 * This task uploads the assembled plugin zip file to a configured registry
 * using the registry's REST API. Unlike the regular release task, this task
 * treats HTTP 409 "DUPLICATE_PLUGIN" errors as non-failing conditions and
 * logs an info message instead of failing the build.
 */
@CompileStatic
class RegistryReleaseIfNotExistsTask extends DefaultTask {
    /**
     * The plugin zip file to be uploaded to the registry.
     * By default, this points to the zip file created by the packagePlugin task.
     */
    @InputFile
    final RegularFileProperty zipFile

    /**
     * The plugin spec file to be uploaded to the registry.
     * By default, this points to the spec file created by the packagePlugin task.
     */
    @InputFile
    @Optional
    final RegularFileProperty specFile

    RegistryReleaseIfNotExistsTask() {
        group = 'Nextflow Plugin'
        description = 'Release the assembled plugin to the registry, skipping if already exists'

        final buildDir = project.layout.buildDirectory.get()
        zipFile = project.objects.fileProperty()
        zipFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}.zip")
        })

        specFile = project.objects.fileProperty()
        specFile.convention(project.provider {
            def file = buildDir.file("resources/main/META-INF/spec.json").asFile
            file.exists() ? project.layout.projectDirectory.file(file.absolutePath) : null
        })
    }

    /**
     * Executes the registry release task with duplicate handling.
     * 
     * This method retrieves the plugin configuration and creates a RegistryClient
     * to upload the plugin zip file to the configured registry endpoint.
     * If the plugin already exists (HTTP 409 DUPLICATE_PLUGIN), it logs an info
     * message and continues without failing.
     * 
     * @throws RegistryReleaseException if the upload fails for reasons other than duplicates
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

        def registryUri = new URI(registryConfig.resolvedUrl)
        def client = new RegistryClient(registryUri, registryConfig.resolvedAuthToken)
        def specFileValue = specFile.isPresent() ? project.file(specFile) : null
        def result = client.releaseIfNotExists(project.name, version, specFileValue, project.file(zipFile), plugin.provider) as Map<String, Object>
        
        if (result.skipped as Boolean) {
            // Plugin already exists - log info message and continue
            project.logger.lifecycle("ℹ️  Plugin '${project.name}' version ${version} already exists in registry [${registryUri}] - skipping upload")
        } else {
            // Celebrate successful plugin upload! 🎉
            project.logger.lifecycle("🎉 SUCCESS! Plugin '${project.name}' version ${version} has been successfully released to Nextflow Registry [${registryUri}]!")
        }
    }
}