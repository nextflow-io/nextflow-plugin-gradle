package io.nextflow.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputFile

/**
 * Gradle task to generate the plugin specification file for a Nextflow plugin.
 *
 * <p>This task creates a JSON specification file (spec.json) that describes the plugin's
 * structure and capabilities. The spec file is used by Nextflow's plugin system to understand
 * what extension points and functionality the plugin provides.
 *
 * <p>This task extends {@link JavaExec} because it needs to execute Java code from the
 * Nextflow core library (specifically {@code nextflow.plugin.spec.PluginSpecWriter}) to
 * generate the spec file. The JavaExec task type provides the necessary infrastructure to:
 * <ul>
 *   <li>Set up a Java process with the correct classpath</li>
 *   <li>Execute a main class with arguments</li>
 *   <li>Handle the execution lifecycle and error reporting</li>
 * </ul>
 *
 * <p>The task automatically checks if the configured Nextflow version supports plugin specs
 * (version 25.09.0 or later). For earlier versions, it creates an empty spec file to maintain
 * compatibility.
 *
 * <p>The generated spec file is placed at {@code build/resources/main/META-INF/spec.json}
 * and is included in the plugin's JAR file.
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
abstract class GenerateSpecTask extends JavaExec {

    /**
     * List of fully qualified class names that represent extension points provided by this plugin.
     * These classes extend or implement Nextflow extension point interfaces.
     */
    @Input
    final ListProperty<String> extensionPoints

    /**
     * The output file where the plugin specification JSON will be written.
     * Defaults to {@code build/resources/main/META-INF/spec.json}.
     */
    @OutputFile
    final RegularFileProperty specFile

    /**
     * Constructor that configures the task to execute the PluginSpecWriter from Nextflow core.
     * Sets up the classpath, main class, and arguments needed to generate the spec file.
     */
    GenerateSpecTask() {
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

    /**
     * Executes the task to generate the plugin spec file.
     * Checks if the Nextflow version supports plugin specs (>= 25.09.0).
     * For unsupported versions, creates an empty spec file instead.
     */
    @Override
    void exec() {
        def config = project.extensions.getByType(NextflowPluginConfig)
        if (!isVersionSupported(config.nextflowVersion)) {
            createEmptySpecFile()
            return
        }
        super.exec()
    }

    /**
     * Determines whether the given Nextflow version supports plugin specifications.
     * Plugin specs are supported in Nextflow version 25.09.0 and later.
     *
     * @param nextflowVersion the Nextflow version string (e.g., "25.09.0-edge")
     * @return true if the version supports plugin specs, false otherwise
     */
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

    /**
     * Creates an empty spec file for backward compatibility with Nextflow versions
     * that don't support plugin specifications.
     */
    private void createEmptySpecFile() {
        specFile.get().asFile.text = ''
    }
}
