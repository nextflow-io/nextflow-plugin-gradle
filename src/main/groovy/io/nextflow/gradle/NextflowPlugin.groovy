package io.nextflow.gradle

import io.nextflow.gradle.registry.RegistryUploadTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion

/**
 * A gradle plugin for nextflow plugin projects.
 */
class NextflowPlugin implements Plugin<Project> {
    private static final int JAVA_TOOLCHAIN_VERSION = 21
    private static final int JAVA_VERSION = 17

    @Override
    void apply(Project project) {
        final config = project.extensions.create('nextflowPlugin', NextflowPluginConfig)

        // -----------------------------------
        // Java/Groovy config
        // -----------------------------------
        project.plugins.apply(GroovyPlugin)
        project.plugins.apply(JavaLibraryPlugin)

        project.java {
            toolchain.languageVersion = JavaLanguageVersion.of(JAVA_TOOLCHAIN_VERSION)
        }

        // <HACK>
        // This is not the best way to set source/target versions, but is currently
        // needed to work around a small bug in the testFixtures jars published by Nextflow
        //
        // It should no longer be needed for Nextflow versions >=25.?.?
        project.tasks.withType(JavaCompile).configureEach { task ->
            if (!task.name.startsWith('compileTest')) {
                task.options.release.set(JAVA_VERSION)
                task.sourceCompatibility = JAVA_VERSION
                task.targetCompatibility = JAVA_VERSION
            }
        }
        project.tasks.withType(GroovyCompile).configureEach { task ->
            task.sourceCompatibility = JAVA_VERSION
            task.targetCompatibility = JAVA_VERSION
        }
        // </HACK>

        // -----------------------------------
        // Common dependencies
        // -----------------------------------
        project.repositories { reps ->
            reps.mavenLocal()
            reps.mavenCentral()
            reps.maven { url = "https://s3-eu-west-1.amazonaws.com/maven.seqera.io/releases" }
        }

        project.afterEvaluate {
            config.validate()
            final nextflowVersion = config.nextflowVersion

            project.dependencies { deps ->
                // required compile-time dependencies for nextflow plugins
                deps.compileOnly "io.nextflow:nextflow:${nextflowVersion}"
                deps.compileOnly "org.slf4j:slf4j-api:1.7.10"
                deps.compileOnly "org.pf4j:pf4j:3.4.1"

                // see https://docs.gradle.org/4.1/userguide/dependency_management.html#sec:module_replacement
                deps.modules {
                    module("commons-logging:commons-logging") { replacedBy("org.slf4j:jcl-over-slf4j") }
                }

                // test-only dependencies (for writing tests)
                deps.testImplementation "org.apache.groovy:groovy"
                deps.testImplementation "io.nextflow:nextflow:${nextflowVersion}"
                deps.testImplementation("org.spockframework:spock-core:2.3-groovy-4.0") {
                    exclude group: 'org.apache.groovy'
                }
                deps.testImplementation('org.spockframework:spock-junit4:2.3-groovy-4.0') {
                    exclude group: 'org.apache.groovy'
                }
                deps.testRuntimeOnly "org.objenesis:objenesis:3.4"
                deps.testRuntimeOnly "net.bytebuddy:byte-buddy:1.14.17"
                deps.testImplementation(testFixtures("io.nextflow:nextflow:${nextflowVersion}"))
                deps.testImplementation(testFixtures("io.nextflow:nf-commons:${nextflowVersion}"))
            }
        }
        // use JUnit 5 platform
        project.test.useJUnitPlatform()

        // -----------------------------------
        // Add plugin details to jar manifest
        // -----------------------------------
        project.afterEvaluate {
            def manifest = new PluginManifest(project)
            project.tasks.withType(Jar).each(manifest::configure)
        }

        // -----------------------------
        // Custom tasks
        // -----------------------------
        // extensionPoints - generates extensions.idx file
        project.tasks.register('extensionPoints', ExtensionPointsTask)
        project.tasks.jar.dependsOn << project.tasks.extensionPoints
        project.tasks.compileTestGroovy.dependsOn << project.tasks.extensionPoints

        // packagePlugin - builds the zip file
        project.tasks.register('packagePlugin', PluginPackageTask)
        project.tasks.packagePlugin.dependsOn << [
            project.tasks.extensionPoints,
            project.tasks.classes
        ]
        project.tasks.assemble.dependsOn << project.tasks.packagePlugin

        // installPlugin - installs plugin to (local) nextflow plugins dir
        project.tasks.register('installPlugin', PluginInstallTask)
        project.tasks.installPlugin.dependsOn << project.tasks.assemble

        // sometimes tests depend on the assembled plugin
        project.tasks.test.dependsOn << project.tasks.assemble

        project.afterEvaluate {
            if (config.publishing) {
                // track the publish tasks
                def publishTasks = []

                // add registry publish task, if configured
                if (config.publishing.registry) {
                    // releasePluginToRegistry - publishes plugin to a plugin registry
                    project.tasks.register('releasePluginToRegistry', RegistryUploadTask)
                    project.tasks.releasePluginToRegistry.dependsOn << project.tasks.packagePlugin
                    publishTasks << project.tasks.releasePluginToRegistry
                }


                // finally, configure the destination-agnostic 'release' task
                if (!publishTasks.isEmpty()) {
                    // releasePlugin - all the release/publishing actions
                    project.tasks.register('releasePlugin', {
                        group = 'Nextflow Plugin'
                        description = 'Release plugin to configured destination'
                    })
                    for (task in publishTasks) {
                        project.tasks.releasePlugin.dependsOn << task
                    }
                }
            }
        }
    }

}
