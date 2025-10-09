# Config Metadata Generation

## Overview

The `nextflow-plugin-gradle` plugin provides automatic generation of JSON metadata from configuration classes annotated with `@Description`, `@ConfigOption`, and `@ScopeName` annotations. This metadata enables IDE autocomplete, validation, and documentation tooling for Nextflow plugin configuration options.

## How It Works

The `ConfigMetadataTask` uses runtime reflection to scan compiled Groovy/Java classes that implement the `ConfigScope` interface and extract annotation metadata. The task:

1. Loads compiled classes from the plugin's classpath
2. Scans specified packages for classes implementing `ConfigScope`
3. Extracts `@ScopeName`, `@Description`, and `@ConfigOption` annotations
4. Generates a schema-compliant JSON file at `build/generated/resources/META-INF/plugin.json`
5. Includes the JSON file in the plugin JAR automatically

### Why Not Annotation Processors?

This implementation uses a Gradle task with runtime reflection instead of Java annotation processors because:

- **Groovy Compatibility**: Annotation processors don't reliably work with Groovy's joint compilation
- **Runtime Annotations**: Existing annotations use `@Retention(RUNTIME)`, which aren't visible to annotation processors
- **Simplicity**: Gradle task approach is easier to debug and maintain
- **No Breaking Changes**: Works with existing annotation definitions

## Usage

### Basic Setup

In your plugin's `build.gradle`:

```groovy
plugins {
    id 'io.nextflow.nextflow-plugin' version '1.0.0-beta.11'
}

nextflowPlugin {
    // ... other plugin configuration ...

    // Enable config metadata generation
    configPackages = ['your.plugin.config.package']
}
```

### Configuration Options

#### `configPackages` (optional)

List of package names to scan for configuration classes.

```groovy
nextflowPlugin {
    // Scan specific packages
    configPackages = ['nextflow.cloud.aws.config', 'nextflow.cloud.aws.batch']

    // Or scan all packages (leave empty or omit)
    configPackages = []
}
```

**Default behavior:** If `configPackages` is empty or not specified, all compiled classes will be scanned.

## Annotations

### Required Annotations

Your configuration classes must use these annotations:

#### `@ConfigScope` Interface

Marker interface that identifies a class as a configuration scope:

```groovy
import nextflow.config.schema.ConfigScope

class AwsConfig implements ConfigScope {
    // ...
}
```

#### `@ScopeName` (optional)

Defines the configuration scope name:

```groovy
import nextflow.config.schema.ScopeName

@ScopeName("aws")
class AwsConfig implements ConfigScope {
    // ...
}
```

#### `@Description` (optional)

Documents configuration scopes and options:

```groovy
import nextflow.script.dsl.Description

@Description("""
    The `aws` scope controls interactions with AWS.
""")
class AwsConfig implements ConfigScope {

    @ConfigOption
    @Description("""
        AWS region (e.g. `us-east-1`).
    """)
    String region
}
```

#### `@ConfigOption` (required for fields)

Marks a field as a configuration option:

```groovy
import nextflow.config.schema.ConfigOption

@ConfigOption
String region

@ConfigOption
Integer maxConnections
```

## Output Format

The generated `META-INF/plugin.json` conforms to the [Nextflow plugin schema v1](https://raw.githubusercontent.com/nextflow-io/schemas/main/plugin/v1/schema.json).

### Example Output

```json
{
  "definitions": [
    {
      "type": "ConfigScope",
      "spec": {
        "name": "aws",
        "description": "The `aws` scope controls interactions with AWS.",
        "children": [
          {
            "type": "ConfigOption",
            "spec": {
              "name": "region",
              "type": "String",
              "description": "AWS region (e.g. `us-east-1`)."
            }
          },
          {
            "type": "ConfigOption",
            "spec": {
              "name": "maxConnections",
              "type": "Integer",
              "description": "Maximum number of connections."
            }
          }
        ]
      }
    }
  ]
}
```

### Schema Structure

- **`definitions`**: Array of configuration scopes
- **`type`**: Always `"ConfigScope"` for scope definitions
- **`spec.name`**: Scope name from `@ScopeName` (optional)
- **`spec.description`**: Scope description from `@Description` (optional)
- **`spec.children`**: Array of configuration options
  - **`type`**: Always `"ConfigOption"` for option definitions
  - **`spec.name`**: Field name
  - **`spec.type`**: Java type name (e.g., `String`, `Integer`, `Boolean`)
  - **`spec.description`**: Option description from `@Description` (optional)

## Build Integration

### Task Dependencies

The `configMetadata` task:
- Depends on `compileGroovy` (runs after compilation)
- Is a dependency of `jar` (runs before JAR packaging)
- Outputs to `build/generated/resources/META-INF/plugin.json`

### Generated Resources

The generated JSON file is automatically included in the plugin JAR:

```
plugin.jar
└── META-INF/
    └── plugin.json
```

### Manual Task Execution

To generate metadata without building the entire plugin:

```bash
./gradlew configMetadata
```

To force regeneration:

```bash
./gradlew configMetadata --rerun-tasks
```

## Complete Example

### Configuration Class

```groovy
package nextflow.cloud.aws.config

import nextflow.config.schema.ConfigOption
import nextflow.config.schema.ConfigScope
import nextflow.config.schema.ScopeName
import nextflow.script.dsl.Description

@ScopeName("aws")
@Description("""
    The `aws` scope controls interactions with AWS, including AWS Batch and S3.
""")
class AwsConfig implements ConfigScope {

    @ConfigOption
    @Description("""
        AWS region (e.g. `us-east-1`).
    """)
    String region

    @ConfigOption
    @Description("""
        AWS account access key.
    """)
    String accessKey

    @ConfigOption
    @Description("""
        AWS account secret key.
    """)
    String secretKey
}
```

### Plugin Build Configuration

```groovy
plugins {
    id 'io.nextflow.nextflow-plugin' version '1.0.0-beta.11'
}

nextflowPlugin {
    nextflowVersion = '25.08.0-edge'
    provider = 'Your Name'
    description = 'AWS cloud integration plugin'
    className = 'nextflow.cloud.aws.AwsPlugin'

    // Enable config metadata generation
    configPackages = ['nextflow.cloud.aws.config']
}
```

### Build and Verify

```bash
# Build the plugin
./gradlew build

# Verify generated metadata
cat build/generated/resources/META-INF/plugin.json

# Verify it's included in JAR
jar tf build/libs/your-plugin.jar | grep plugin.json
```

## Troubleshooting

### No Metadata Generated

**Problem:** Task runs but generates empty `definitions` array.

**Solutions:**
1. Ensure `configPackages` includes the correct package names
2. Verify config classes implement `ConfigScope` interface
3. Check that classes are compiled (run `compileGroovy` first)
4. Enable debug logging: `./gradlew configMetadata --debug`

### Classes Not Found

**Problem:** `ClassNotFoundException` or `NoClassDefFoundError` in logs.

**Solutions:**
1. Ensure all dependencies are available at compile time
2. Check that annotation classes are in `compileOnly` dependencies
3. Verify classpath includes both `classesDirs` and `compileClasspath`

### Groovy Method Dispatch Errors

**Problem:** `Could not find method` errors during task execution.

**Cause:** Groovy's dynamic method dispatch doesn't work across classloaders.

**Solution:** The task already handles this by inlining all method calls and using `invokeMethod()` for annotation value access. If you see this error, report it as a bug.

### Annotations Not Visible

**Problem:** `@Description` or `@ConfigOption` values are null or missing.

**Solutions:**
1. Verify annotations have `@Retention(RetentionPolicy.RUNTIME)`
2. Check annotation imports are correct
3. Ensure annotations are on the correct elements (TYPE, FIELD)

## Advanced Usage

### Custom Output Location

The output file location is configurable via task properties:

```groovy
tasks.named('configMetadata') {
    outputFile = layout.buildDirectory.file('custom/path/metadata.json')
}
```

### Additional Processing

To post-process the generated JSON:

```groovy
tasks.register('validateMetadata') {
    dependsOn configMetadata

    doLast {
        def json = file('build/generated/resources/META-INF/plugin.json')
        // Validate against schema, generate TypeScript definitions, etc.
    }
}
```

## Related Documentation

- [Nextflow Plugin Schema v1](https://raw.githubusercontent.com/nextflow-io/schemas/main/plugin/v1/schema.json)
- [Nextflow Plugin Development Guide](https://www.nextflow.io/docs/latest/plugins.html)
- [nf-amazon Example](https://github.com/nextflow-io/nextflow/tree/master/plugins/nf-amazon) - Reference implementation

## Version History

- **1.0.0-beta.11** (Oct 2025)
  - Initial release of config metadata generation
  - Schema-compliant JSON output
  - Support for @Description, @ConfigOption, @ScopeName annotations
