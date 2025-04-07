package io.nextflow.gradle.registry

import groovy.transform.CompileStatic
import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

@CompileStatic
class RegistryUploadTask extends DefaultTask {
    @InputFile
    final RegularFileProperty zipFile

    RegistryUploadTask() {
        group = 'Nextflow Plugin'
        description = 'Publish the assembled plugin to a registry'

        final buildDir = project.layout.buildDirectory.get()
        zipFile = project.objects.fileProperty()
        zipFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}.zip")
        })
    }


    @TaskAction
    def run() {
        final version = project.version.toString()
        final plugin = project.extensions.getByType(NextflowPluginConfig)
        final config = plugin.publishing.registry

        def client = new RegistryClient(new URI(config.url), config.authToken)
        client.publish(project.name, version, project.file(zipFile))
    }
}
