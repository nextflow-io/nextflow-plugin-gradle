package io.nextflow.gradle

import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

/**
 * Utility class to customise manifest file of Gradle jar task
 */
class PluginManifest {
    private final Project project

    PluginManifest(Project project) {
        this.project = project
    }

    void configure(Jar jar) {
        final config = project.extensions.getByType(NextflowPluginConfig)

        jar.manifest.attributes(
            'Plugin-Id': project.name,
            'Plugin-Version': project.version,
            'Plugin-Requires': ">=${config.nextflowVersion}"
        )
        if (config.className) {
            jar.manifest.attributes('Plugin-Class': config.className)
        }
        if (config.provider) {
            jar.manifest.attributes('Plugin-Provider': config.provider)
        }
        if (config.description) {
            jar.manifest.attributes('Plugin-Description': config.description)
        }
        if (config.requirePlugins) {
            jar.manifest.attributes('Plugin-Dependencies': config.requirePlugins.join(','))
        }
    }
}
