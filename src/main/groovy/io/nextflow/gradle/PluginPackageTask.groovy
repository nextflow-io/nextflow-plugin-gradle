package io.nextflow.gradle

import org.apache.commons.io.FilenameUtils
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip

/**
 * Gradle task to package a Nextflow plugin into a zip file
 */
abstract class PluginPackageTask extends Zip {

    PluginPackageTask() {
        group = 'Nextflow Plugin'
        description = 'Package up this Nextflow plugin for deployment'

        into('classes') { with project.tasks.jar }
        into('lib') { from project.configurations.runtimeClasspath }
        preserveFileTimestamps = false
        reproducibleFileOrder = true
    }

    @Override
    protected void copy() {
        // first do a sanity check
        def config = project.extensions.getByType(NextflowPluginConfig)
        checkPluginClassIncluded(config.className)

        // then call parent class to perform the task
        super.copy()
    }

    // Scan the sources to check that the declared main plugin classes is included
    protected void checkPluginClassIncluded(String className) {
        def sourceSets = project.extensions.getByType(SourceSetContainer)
            .named(SourceSet.MAIN_SOURCE_SET_NAME).get()

        // get a list of resources dirs to be ignored
        def resourcesDirs = sourceSets.getResources().getSourceDirectories()
        // get all the sources dirs (which includes resources)
        def sources = sourceSets.getAllSource()

        // try to find the plugin class in the source dirs
        def classNameAsPath = className.replaceAll('\\.', File.separator)
        def matched = sources.find { file ->
            def name = FilenameUtils.removeExtension(file.absolutePath)
            for (def dir : sources.getSourceDirectories()) {
                if (!resourcesDirs.contains(dir) && name.startsWith(dir.absolutePath)) {
                    name = name.substring(dir.absolutePath.length()+1)
                    break
                }
            }
            return name == classNameAsPath
        }

        if (!matched) {
            def message = "--------------------------------------------------------------------------------\n" +
                "Plugin class '$className' not found in source directories:\n"
            for (def dir : sources.getSourceDirectories()) {
                if (!resourcesDirs.contains(dir)) message += "\n- $dir"
            }
            message += "\n--------------------------------------------------------------------------------"
            throw new RuntimeException(message)
        }
    }
}
