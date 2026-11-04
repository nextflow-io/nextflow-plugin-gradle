# ADR: Plugin Specification Generation Task

## Context

Nextflow plugins require a machine-readable specification file that describes their capabilities and extension points. The `GenerateSpecTask` automates the generation of this specification file (`spec.json`) using Nextflow's built-in tooling.

## Implementation

### Task Type
- **Extends**: `JavaExec` (not a standard task)
- **Rationale**: Must execute Java code from Nextflow core library to generate the specification
- **Location**: `src/main/groovy/io/nextflow/gradle/GenerateSpecTask.groovy:34`

### What Runs

The task executes the Java class `nextflow.plugin.spec.PluginSpecWriter` from Nextflow core:

```groovy
getMainClass().set('nextflow.plugin.spec.PluginSpecWriter')
```

**Arguments**: `[specFile path] + [list of extension point class names]`

Example: `/path/to/spec.json com.example.MyExecutor com.example.MyTraceObserver`

### Nextflow Dependency

**Minimum Version**: Nextflow **25.09.0**
- Versions >= 25.09.0: Executes `PluginSpecWriter` to generate full specification
- Versions < 25.09.0: Creates empty spec file for backward compatibility

**Dependency Resolution**: Uses dedicated `specFile` source set and configuration

```groovy
configurations.create('specFile')
sourceSets.create('specFile') {
    compileClasspath += configurations.specFile
    runtimeClasspath += configurations.specFile
}
```

**Classpath includes**:
- `io.nextflow:nextflow:${nextflowVersion}` - provides PluginSpecWriter class
- Plugin's own JAR file - provides extension point classes for introspection

### Output Format & Location

**Format**: JSON file
**Path**: `build/resources/main/META-INF/spec.json`
**Packaging**: Included in plugin JAR at `META-INF/spec.json`

The specification describes plugin structure and capabilities for Nextflow's plugin system to discover.

### Task Configuration

**Inputs**:
- `extensionPoints`: List of fully qualified class names implementing Nextflow extension points

**Outputs**:
- `specFile`: The generated JSON specification

**Execution order**:
1. Compile plugin classes (`jar`)
2. Compile specFile source set (`compileSpecFileGroovy`)
3. Execute `buildSpec` task

### Version Detection Logic

Simple integer parsing of `major.minor.patch` format:
- Splits on first two dots
- Compares: `major >= 25 && minor >= 9`
- Handles edge suffixes: `25.09.0-edge` → supported

## Decision

Generate plugin specification using Nextflow's own tooling rather than custom implementation to ensure:
- Compatibility with Nextflow's plugin system evolution
- Correct introspection of extension point capabilities
- Consistency across plugin ecosystem

## Consequences

**Positive**:
- Delegates specification format to Nextflow core
- Automatic compatibility with Nextflow's plugin discovery
- Empty file fallback maintains compatibility with older Nextflow versions

**Negative**:
- Requires JavaExec complexity instead of simple file generation
- Circular dependency: needs compiled plugin JAR before generating spec
- Hard version cutoff at 25.09.0 (no graceful degradation between this version)