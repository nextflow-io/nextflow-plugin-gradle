package io.nextflow.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile

/**
 * Gradle task to generate the plugin spec file.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
abstract class BuildSpecTask extends JavaExec {

    @Input
    final ListProperty<String> extensionPoints

    @OutputFile
    final RegularFileProperty specFile

    BuildSpecTask() {
        extensionPoints = project.objects.listProperty(String)
        extensionPoints.convention(project.provider {
            project.extensions.getByType(NextflowPluginConfig).extensionPoints
        })

        specFile = project.objects.fileProperty()
        specFile.convention(project.layout.buildDirectory.file("resources/main/META-INF/spec.json"))

        getMainClass().set('nextflow.plugin.spec.PluginSpecWriter')

        project.afterEvaluate {
            setClasspath(project.sourceSets.getByName('specFile').runtimeClasspath)
            setArgs([specFile.get().asFile.toString()] + extensionPoints.get())
        }

        doFirst {
            specFile.get().asFile.parentFile.mkdirs()
        }
    }

    @Override
    void exec() {
        def config = project.extensions.getByType(NextflowPluginConfig)
        if (!isVersionSupported(config.nextflowVersion)) {
            createEmptySpecFile()
            return
        }
        super.exec()
    }

    private boolean isVersionSupported(String nextflowVersion) {
        try {
            def parts = nextflowVersion.split(/\./, 3)
            if (parts.length < 3)
                return false
            def major = Integer.parseInt(parts[0])
            def minor = Integer.parseInt(parts[1])
            return major >= 25 && minor >= 9
        } catch (Exception e) {
            project.logger.warn("Unable to parse Nextflow version '${nextflowVersion}', assuming plugin spec is not supported: ${e.message}")
            return false
        }
    }

    private void createEmptySpecFile() {
        specFile.get().asFile.text = ''
    }
}
