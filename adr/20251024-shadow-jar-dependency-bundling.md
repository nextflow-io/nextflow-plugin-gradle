# ADR: Shadow JAR for Dependency Bundling

## Context

The `nextflow-plugin-gradle` plugin depends on `io.seqera:npr-api:0.15.0`, which is published to Seqera's private Maven repository (`https://s3-eu-west-1.amazonaws.com/maven.seqera.io/releases`), not Maven Central or the Gradle Plugin Portal.

When plugin projects apply `nextflow-plugin-gradle` via `includeBuild` or from a published artifact, Gradle attempts to resolve `npr-api` from the default plugin repositories, causing build failures:

```
Could not find io.seqera:npr-api:0.15.0.
Searched in the following locations:
  - https://plugins.gradle.org/m2/io/seqera/npr-api/0.15.0/npr-api-0.15.0.pom
```

**Previous workaround**: Required consumers to manually add Seqera's Maven repository to their `pluginManagement` block, which is not user-friendly and error-prone.

## Implementation

### Shadow JAR Configuration

**Plugin Applied**: `com.gradleup.shadow` version `9.0.0-beta6`

**Dependency Strategy**: Use `compileOnly` for all bundled dependencies

```groovy
dependencies {
    compileOnly 'commons-io:commons-io:2.18.0'
    compileOnly 'com.github.zafarkhaja:java-semver:0.10.2'
    compileOnly 'com.google.code.gson:gson:2.10.1'
    compileOnly 'io.seqera:npr-api:0.15.0'
    compileOnly 'com.fasterxml.jackson.core:jackson-databind:2.18.2'
    compileOnly 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2'
}
```

**Rationale**: `compileOnly` dependencies are:
- Available at compile time for the plugin code
- NOT exposed to consumers via Gradle metadata
- NOT added to POM/module dependencies
- Still bundled by Shadow plugin when explicitly configured

### Shadow JAR Task Configuration

```groovy
shadowJar {
    archiveClassifier = ''
    configurations = [project.configurations.compileClasspath]

    // Relocate dependencies to avoid conflicts
    relocate 'com.google.gson', 'io.nextflow.shadow.gson'
    relocate 'com.fasterxml.jackson', 'io.nextflow.shadow.jackson'
    relocate 'org.apache.commons.io', 'io.nextflow.shadow.commons.io'

    // Exclude Groovy - provided by Gradle
    exclude 'org/codehaus/groovy/**'
    exclude 'groovy/**'
}
```

**Key Configuration**:
- `archiveClassifier = ''` - Produces main artifact (not `-all` suffix)
- `configurations = [compileClasspath]` - Includes compileOnly dependencies
- Package relocation prevents classpath conflicts with consumer projects
- Groovy excluded as it's provided by Gradle runtime

### JAR Replacement Strategy

```groovy
jar {
    enabled = false
    dependsOn(shadowJar)
}
assemble.dependsOn(shadowJar)
```

**Why disable standard jar**:
- Gradle Plugin Development expects `-main.jar` for `includeBuild`
- Shadow JAR becomes the primary artifact
- Only one JAR is published/used

### Component Metadata Configuration

```groovy
components.java.withVariantsFromConfiguration(configurations.shadowRuntimeElements) {
    skip()
}

afterEvaluate {
    configurations.runtimeElements.outgoing.artifacts.clear()
    configurations.runtimeElements.outgoing.artifact(shadowJar)
    configurations.apiElements.outgoing.artifacts.clear()
    configurations.apiElements.outgoing.artifact(shadowJar)
}
```

**Purpose**: Replace default JAR artifacts with Shadow JAR in Gradle's component metadata for both runtime and API variants.

### Test Configuration

```groovy
dependencies {
    testRuntimeOnly 'commons-io:commons-io:2.18.0'
    testRuntimeOnly 'com.github.zafarkhaja:java-semver:0.10.2'
    // ... (all bundled dependencies)
}
```

**Why needed**: Tests run in isolation and need actual dependencies at runtime, not just the shadow JAR.

## Technical Facts

### Artifact Characteristics

**Size**: 8.4 MB (includes all dependencies)
- Base plugin classes: ~80 KB
- Bundled dependencies: ~8.3 MB

**Contents verification**:
```bash
$ unzip -l build/libs/nextflow-plugin-gradle-1.0.0-beta.10.jar | grep "io/seqera/npr" | wc -l
36
```

**Package relocation**:
- Original: `com.google.gson.*`
- Relocated: `io.nextflow.shadow.gson.*`
- Original: `io.seqera.npr.*`
- Kept: `io.seqera.npr.*` (no relocation, internal API)

### Gradle Metadata

**POM dependencies**: None (compileOnly not published)
**Module metadata**: Shadow JAR in runtime/API variants, no transitive dependencies

### includeBuild Compatibility

Works with `pluginManagement { includeBuild '../nextflow-plugin-gradle' }`:
- No additional repository configuration required
- Shadow JAR available on plugin classpath
- All dependencies self-contained

## Decision

Use Shadow JAR with `compileOnly` dependencies and package relocation to create a self-contained plugin artifact that:
1. Bundles all dependencies (including `npr-api` from private repositories)
2. Does not expose transitive dependencies to consumers
3. Relocates common libraries to avoid classpath conflicts
4. Works with both `includeBuild` and published artifacts

## Consequences

### Positive

**Zero consumer configuration**: Plugin users don't need to configure Seqera's Maven repository or manage transitive dependencies.

**Classpath isolation**: Package relocation prevents conflicts when consumers use different versions of Gson, Jackson, or Commons IO.

**includeBuild support**: Development workflow using composite builds works without publishToMavenLocal.

**Distribution simplicity**: Single JAR artifact contains everything needed to run the plugin.

### Negative

**Artifact size**: 8.4 MB vs ~80 KB for base plugin (105x larger)
- Acceptable for Gradle plugin distribution
- One-time download cost

**Build complexity**:
- Shadow plugin configuration required
- Component metadata manipulation
- Duplicate dependencies in test configuration

**Maintenance overhead**:
- Must keep relocation rules updated if new conflicting dependencies added
- Need to exclude Gradle-provided libraries (Groovy, etc.)

**Version conflicts**: If consumers need different versions of relocated dependencies via plugin's extension points, they cannot override them (sealed in shadow JAR).

### Alternative Considered and Rejected

**Repository in pluginManagement**: Requires manual configuration by every consumer project - rejected for poor user experience.

**Publish to Maven Central**: Not viable - `npr-api` is Seqera's internal library, not suitable for public repository.

**Dependency substitution**: Would still require consumers to add repository and manage versions - doesn't solve core problem.
