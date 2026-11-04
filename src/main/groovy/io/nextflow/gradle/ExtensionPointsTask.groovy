package io.nextflow.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
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

        // a plugin may ship its own index in src/main/resources instead
        if (!config.extensionPoints && hasResourceIndex())
            return

        // write an empty index when there are no extension points, otherwise
        // pf4j falls back to the Nextflow runtime's index and registers its
        // extensions a second time under this plugin
        def index = project.file(outputFile)
        index.parentFile.mkdirs()
        index.text = config.extensionPoints ? config.extensionPoints.join("\n") + "\n" : ''
    }

    private boolean hasResourceIndex() {
        final main = project.extensions.getByType(SourceSetContainer).getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        return !main.resources.matching { include 'META-INF/extensions.idx' }.isEmpty()
    }
}
