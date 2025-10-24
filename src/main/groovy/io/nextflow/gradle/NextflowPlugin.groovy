package io.nextflow.gradle

import io.nextflow.gradle.registry.RegistryReleaseIfNotExistsTask
import io.nextflow.gradle.registry.RegistryReleaseTask
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

        // Create specFile source set early so configurations are available
        if( config.generateSpec ) {
            project.configurations.create('specFile')
            if (!project.sourceSets.findByName('specFile')) {
                project.sourceSets.create('specFile') { sourceSet ->
                    sourceSet.compileClasspath += project.configurations.getByName('specFile')
                    sourceSet.runtimeClasspath += project.configurations.getByName('specFile')
                }
            }
        }

        project.afterEvaluate {
            config.validate()
            final nextflowVersion = config.nextflowVersion

            if (config.useDefaultDependencies) {
                addDefaultDependencies(project, nextflowVersion)
            }

            // dependencies for generateSpec task
            if( config.generateSpec ) {
                project.dependencies { deps ->
                    deps.specFile "io.nextflow:nextflow:${nextflowVersion}"
                    deps.specFile project.files(project.tasks.jar.archiveFile)
                }
            }
        }

        // use JUnit 5 platform
        project.test.useJUnitPlatform()

        // sometimes tests depend on the assembled plugin
        project.tasks.test.dependsOn << project.tasks.assemble

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
        def extensionPointsTask = project.tasks.register('extensionPoints', ExtensionPointsTask)
        project.tasks.jar.dependsOn << extensionPointsTask
        // ensure the generated extensions.idx is included in the JAR with correct path
        project.tasks.jar.from(project.layout.buildDirectory.dir('resources/main'))
        project.tasks.compileTestGroovy.dependsOn << extensionPointsTask

        // buildSpec - generates the plugin spec file
        if( config.generateSpec ) {
            project.tasks.register('buildSpec', GenerateSpecTask)
            project.tasks.buildSpec.dependsOn << [
                project.tasks.jar,
                project.tasks.compileSpecFileGroovy
            ]
        }

        // packagePlugin - builds the zip file
        project.tasks.register('packagePlugin', PluginPackageTask)
        project.tasks.packagePlugin.dependsOn << [
            project.tasks.extensionPoints,
            project.tasks.classes
        ]
        project.afterEvaluate {
            if( config.generateSpec )
                project.tasks.packagePlugin.dependsOn << project.tasks.buildSpec
        }
        project.tasks.assemble.dependsOn << project.tasks.packagePlugin

        // installPlugin - installs plugin to (local) nextflow plugins dir
        project.tasks.register('installPlugin', PluginInstallTask)
        project.tasks.installPlugin.dependsOn << project.tasks.assemble

        // releasePlugin - publish plugin release to registry
        project.afterEvaluate {
            // Always create registry release task - it will use fallback configuration if needed
            project.tasks.register('releasePluginToRegistry', RegistryReleaseTask)
            project.tasks.releasePluginToRegistry.dependsOn << project.tasks.packagePlugin

            // Always create the main release task
            project.tasks.register('releasePlugin', {
                group = 'Nextflow Plugin'
                description = 'Release plugin to configured destination'
            })
            project.tasks.releasePlugin.dependsOn << project.tasks.releasePluginToRegistry

            // Always create registry release if not exists task - handles duplicates gracefully
            project.tasks.register('releasePluginToRegistryIfNotExists', RegistryReleaseIfNotExistsTask)
            project.tasks.releasePluginToRegistryIfNotExists.dependsOn << project.tasks.packagePlugin

            // Always create the main release if not exists task
            project.tasks.register('releasePluginIfNotExists', {
                group = 'Nextflow Plugin'
                description = 'Release plugin to configured destination, skipping if already exists'
            })
            project.tasks.releasePluginIfNotExists.dependsOn << project.tasks.releasePluginToRegistryIfNotExists
        }
    }

    private void addDefaultDependencies(Project project, String nextflowVersion) {
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

}
