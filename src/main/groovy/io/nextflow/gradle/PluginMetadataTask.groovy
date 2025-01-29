package io.nextflow.gradle

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Gradle task to create the Nextflow plugin metadata file
 */
class PluginMetadataTask extends DefaultTask {
    @InputFile
    final RegularFileProperty inputFile
    @OutputFile
    final RegularFileProperty outputFile

    PluginMetadataTask() {
        final buildDir = project.layout.buildDirectory.get()
        inputFile = project.objects.fileProperty()
        inputFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}.zip")
        })

        outputFile = project.objects.fileProperty()
        outputFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}-meta.json")
        })
    }

    @TaskAction
    def run() {
        final config = project.extensions.getByType(NextflowPluginConfig)
        final metadata = [
            version  : "${project.version}",
            date     : OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            url      : config.publishing.url(),
            requires : ">=${config.nextflowVersion}",
            sha512sum: computeSha512(project.file(inputFile))
        ]
        project.file(outputFile).text = JsonOutput.prettyPrint(JsonOutput.toJson(metadata))
    }

    private static String computeSha512(File file) {
        if (!file.exists()) {
            throw new GradleException("Missing file: $file -- cannot compute SHA-512")
        }
        MessageDigest.getInstance("SHA-512").digest(file.bytes).encodeHex().toString()
    }
}
