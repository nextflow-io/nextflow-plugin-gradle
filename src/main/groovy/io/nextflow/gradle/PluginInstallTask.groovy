package io.nextflow.gradle

import org.gradle.api.tasks.Sync

/**
 * Gradle task which 'installs' (copies) this Nextflow plugin into
 * the local Nextflow plugins dir.
 */
abstract class PluginInstallTask extends Sync {

    PluginInstallTask() {
        group = 'Nextflow Plugin'
        description = 'Install this plugin into your local Nextflow plugins dir'

        final buildDir = project.layout.buildDirectory.get()
        def (pluginsDir, reason) = getNextflowPluginsDir()
        final config = project.extensions.getByType(NextflowPluginConfig)

        project.afterEvaluate {
            from(project.zipTree("${buildDir}/distributions/${project.name}-${project.version}.zip"))
            into("${pluginsDir}/${config.pluginId}-${project.version}")
        }

        doLast {
            println "Plugin ${config.pluginId} installed successfully!"
            println "Installation location: ${destinationDir}"
            println "Installation location determined by - $reason"
        }
    }

    static def getNextflowPluginsDir() {
        // first look for explicit NXF_PLUGINS_DIR
        def prop = System.getenv('NXF_PLUGINS_DIR')
        if (prop) {
            return [prop, "NXF_PLUGINS_DIR environment variable"]
        }

        // next look for explicit NXF_HOME
        prop = System.getenv('NXF_HOME')
        if (prop) {
            return ["${prop}/plugins", "NXF_HOME environment variable"]
        }

        // otherwise, assume default
        prop = System.getProperty('user.home')
        ["${prop}/.nextflow/plugins", "Default location (~/.nextflow/plugins)"]
    }
}
