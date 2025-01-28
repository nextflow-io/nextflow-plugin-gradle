package io.nextflow.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task to generate extensions.idx file from the list
 * of classnames specified in build.gradle.
 */
class ExtensionPointsTask extends DefaultTask {
    @Input
    final ListProperty<String> extensionPoints
    @OutputFile
    final RegularFileProperty outputFile

    ExtensionPointsTask() {
        extensionPoints = project.objects.listProperty(String)
        extensionPoints.convention(project.provider {
            project.extensions.getByType(NextflowPluginConfig).extensionPoints
        })

        final buildDir = project.layout.buildDirectory.get()
        outputFile = project.objects.fileProperty()
        outputFile.convention(project.provider {
            buildDir.file("resources/main/META-INF/extensions.idx")
        })
    }

    @TaskAction
    def run() {
        final config = project.extensions.getByType(NextflowPluginConfig)

        // write the list of extension points from build.gradle
        // to extensions.idx file
        if (config.extensionPoints) {
            def index = project.file(outputFile)
            index.parentFile.mkdirs()
            index.text = config.extensionPoints.join("\n") + "\n"
        }
    }
}
