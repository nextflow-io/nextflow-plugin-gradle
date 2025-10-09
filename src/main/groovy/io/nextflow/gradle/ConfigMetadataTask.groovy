package io.nextflow.gradle

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.Closeable

/**
 * Gradle task to generate JSON metadata from {@code @Description}, {@code @ConfigOption},
 * and {@code @ScopeName} annotations on configuration classes.
 *
 * <p>This task scans compiled classes for configuration metadata annotations and generates
 * a single {@code plugin.json} file in {@code META-INF/} that conforms to the Nextflow
 * plugin schema.</p>
 *
 * <p>Generated JSON structure:</p>
 * <pre>
 * {
 *   "definitions": [
 *     {
 *       "type": "ConfigScope",
 *       "spec": {
 *         "name": "aws",
 *         "description": "The `aws` scope controls interactions with AWS...",
 *         "children": [
 *           {
 *             "type": "ConfigOption",
 *             "spec": {
 *               "name": "region",
 *               "type": "String",
 *               "description": "AWS region (e.g. `us-east-1`)."
 *             }
 *           }
 *         ]
 *       }
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author Paolo Di Tommaso
 */
class ConfigMetadataTask extends DefaultTask {

    /**
     * List of package names to scan for configuration classes.
     * If not specified, the task will attempt to scan all packages.
     */
    @Input
    @Optional
    final ListProperty<String> configPackages

    /**
     * The compiled classes directories to scan for configuration classes.
     */
    @InputFiles
    FileCollection classesDirs

    /**
     * The classpath needed to load the classes (includes dependencies).
     */
    @InputFiles
    FileCollection classpath

    /**
     * Output file where the plugin.json metadata will be generated.
     * Defaults to {@code build/generated/resources/META-INF/plugin.json}.
     */
    @OutputFile
    final RegularFileProperty outputFile

    ConfigMetadataTask() {
        group = 'nextflow plugin'
        description = 'Generate JSON metadata from @Description annotations on config classes'

        configPackages = project.objects.listProperty(String)
        configPackages.convention([])

        outputFile = project.objects.fileProperty()
        outputFile.convention(project.provider {
            project.layout.buildDirectory.file("generated/resources/META-INF/plugin.json").get()
        })

        // Default to main source set
        // Use compileClasspath instead of runtimeClasspath because plugins typically
        // use compileOnly for nextflow dependencies
        classesDirs = project.sourceSets.main.output.classesDirs
        classpath = project.sourceSets.main.compileClasspath
    }

    @TaskAction
    void generateMetadata() {
        def outputFileObj = outputFile.get().asFile
        outputFileObj.parentFile.mkdirs()

        // Create class loader with compiled classes and dependencies
        def urls = (classesDirs.files + classpath.files).collect { it.toURI().toURL() } as URL[]
        def classLoader = new URLClassLoader(urls, (ClassLoader) null)

        try {
            // Load annotation classes dynamically to avoid compile-time dependencies
            def ConfigScope = loadClassSafely(classLoader, 'nextflow.config.schema.ConfigScope')
            def ConfigOption = loadClassSafely(classLoader, 'nextflow.config.schema.ConfigOption')
            def ScopeName = loadClassSafely(classLoader, 'nextflow.config.schema.ScopeName')
            def Description = loadClassSafely(classLoader, 'nextflow.script.dsl.Description')

            if (!ConfigScope || !ConfigOption || !ScopeName || !Description) {
                project.logger.info("ConfigMetadataTask: Required annotation classes not found, skipping metadata generation")
                return
            }

            // Collect all config scope definitions
            def definitions = []
            int processedCount = 0

            // Determine packages to scan
            def packages = configPackages.get()
            if (packages.isEmpty()) {
                // If no packages specified, scan all classes
                project.logger.debug("ConfigMetadataTask: No packages specified, scanning all compiled classes")
                packages = findAllPackages(classesDirs)
            }

            // Process each package
            packages.each { pkg ->
                def pkgPath = pkg.replace('.', '/')
                classesDirs.each { classesDir ->
                    def pkgDir = new File(classesDir, pkgPath)
                    if (pkgDir.exists() && pkgDir.isDirectory()) {
                        pkgDir.listFiles()?.each { file ->
                            if (file.name.endsWith('.class') && !file.name.contains('$')) {
                                def className = "${pkg}.${file.name[0..-7]}"
                                try {
                                    def clazz = classLoader.loadClass(className)
                                    if (ConfigScope.isAssignableFrom(clazz)) {
                                        // Build schema-compliant definition
                                        def scopeSpec = [:]

                                        // Extract @ScopeName annotation
                                        def scopeNameAnnot = clazz.getAnnotation(ScopeName)
                                        if (scopeNameAnnot) {
                                            scopeSpec.name = scopeNameAnnot.invokeMethod('value', null)
                                        }

                                        // Extract class-level @Description annotation
                                        def descAnnot = clazz.getAnnotation(Description)
                                        if (descAnnot) {
                                            def descValue = descAnnot.invokeMethod('value', null)
                                            scopeSpec.description = descValue?.stripIndent()?.trim() ?: ""
                                        }

                                        // Extract field metadata as children
                                        def children = []
                                        clazz.getDeclaredFields().each { field ->
                                            def configOption = field.getAnnotation(ConfigOption)
                                            if (configOption) {
                                                def optionSpec = [
                                                    name: field.name,
                                                    type: field.type.simpleName
                                                ]

                                                // Extract field-level @Description
                                                def fieldDesc = field.getAnnotation(Description)
                                                if (fieldDesc) {
                                                    def fieldDescValue = fieldDesc.invokeMethod('value', null)
                                                    optionSpec.description = fieldDescValue?.stripIndent()?.trim() ?: ""
                                                }

                                                children.add([
                                                    type: "ConfigOption",
                                                    spec: optionSpec
                                                ])
                                            }
                                        }

                                        if (children) {
                                            scopeSpec.children = children
                                        }

                                        if (scopeSpec) {
                                            definitions.add([
                                                type: "ConfigScope",
                                                spec: scopeSpec
                                            ])
                                            project.logger.lifecycle("ConfigMetadataTask: Processed ${className}")
                                            processedCount++
                                        }
                                    }
                                } catch (ClassNotFoundException e) {
                                    project.logger.debug("ConfigMetadataTask: Could not load class ${className}: ${e.message}")
                                } catch (NoClassDefFoundError e) {
                                    project.logger.debug("ConfigMetadataTask: Missing dependency for ${className}: ${e.message}")
                                } catch (Exception e) {
                                    project.logger.warn("ConfigMetadataTask: Error processing ${className}: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }

            // Write single plugin.json file with all definitions
            def pluginMetadata = [definitions: definitions]
            outputFileObj.text = JsonOutput.prettyPrint(JsonOutput.toJson(pluginMetadata))
            project.logger.lifecycle("ConfigMetadataTask: Generated metadata for ${processedCount} configuration class(es) in ${outputFileObj.name}")

        } finally {
            // Clean up class loader resources
            if (classLoader instanceof Closeable) {
                classLoader.close()
            }
        }
    }

    /**
     * Find all packages in the compiled classes directories.
     */
    private List<String> findAllPackages(FileCollection classesDirs) {
        def packages = [] as Set<String>
        classesDirs.each { classesDir ->
            if (classesDir.exists()) {
                classesDir.eachFileRecurse { file ->
                    if (file.isFile() && file.name.endsWith('.class')) {
                        def relativePath = classesDir.toPath().relativize(file.toPath()).toString()
                        def pkg = relativePath.replace(File.separator, '.').replaceAll(/\.[^.]+\.class$/, '')
                        if (pkg.contains('.')) {
                            packages.add(pkg.substring(0, pkg.lastIndexOf('.')))
                        }
                    }
                }
            }
        }
        return packages.toList()
    }

    /**
     * Safely load a class, returning null if not found.
     */
    private Class<?> loadClassSafely(ClassLoader loader, String className) {
        try {
            return loader.loadClass(className)
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            project.logger.debug("ConfigMetadataTask: Class not available: ${className}")
            return null
        }
    }
}
