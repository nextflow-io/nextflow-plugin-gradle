package io.nextflow.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile

/**
 * Gradle task to generate index file of the plugin definitions.
 */
class BuildIndexTask extends JavaExec {

    @Input
    final ListProperty<String> extensionPoints

    @OutputFile
    final RegularFileProperty outputFile

    BuildIndexTask() {
        extensionPoints = project.objects.listProperty(String)
        extensionPoints.convention(project.provider {
            project.extensions.getByType(NextflowPluginConfig).extensionPoints
        })

        outputFile = project.objects.fileProperty()
        outputFile.convention(project.layout.buildDirectory.file("resources/main/META-INF/index.json"))

        getMainClass().set('nextflow.plugin.index.PluginIndexWriter')

        project.afterEvaluate {
            setClasspath(project.sourceSets.getByName('indexFile').runtimeClasspath)
            setArgs([outputFile.get().asFile.toString()] + extensionPoints.get())
        }

        doFirst {
            outputFile.get().asFile.parentFile.mkdirs()
        }
    }
}
