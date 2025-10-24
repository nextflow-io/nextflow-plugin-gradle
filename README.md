# Gradle plugin for Nextflow

This is a [Gradle](https://gradle.org/) plugin for building [Nextflow](https://www.nextflow.io/) plugins.

It sets up default dependencies required for integration with Nextflow, and adds some custom gradle tasks
to help build and publish your Nextflow plugin.

## Configuration

Apply and configure this plugin in your `build.gradle` file, for example:

```gradle
plugins {
    id 'io.nextflow.nextflow-plugin' version '1.0.0-beta.11'
}

dependencies {
    // (optional) put any library dependencies here
}

// plugin version
version = '0.0.1'

nextflowPlugin {
    // minimum nextflow version
    nextflowVersion = '25.04.0'

    provider = 'Example Inc'
    description = 'My example plugin'
    className = 'com.example.ExamplePlugin'
    extensionPoints = [
        'com.example.ExampleObserver',
        'com.example.ExampleFunctions'
    ]
}
```

### Configuration Options

The `nextflowPlugin` block supports the following configuration options:

- **`nextflowVersion`** (required) - Specifies the minimum Nextflow version required by the plugin
- **`className`** (required) - The fully qualified name of the main plugin class
- **`provider`** (required) - The plugin provider/author name
- **`description`** (optional) - A short description of the plugin
- **`requirePlugins`** (optional) - List of plugin dependencies that must be present
- **`extensionPoints`** (optional) - List of extension point class names provided by the plugin
- **`useDefaultDependencies`** (optional, default: `true`) - Whether to automatically add default dependencies required for Nextflow plugin development
- **`generateSpec`** (optional, default: `true`) - Whether to generate a plugin spec file during the build. Set to `false` to skip spec file generation

### Registry Configuration

The `registry` block is optional and supports several configuration methods:

**Option 1: Explicit configuration**
```gradle
registry {
    url = 'https://registry.nextflow.io/api'
    apiKey = 'your-api-key'
}
```

**Option 2: Using project properties**
Define `npr.apiUrl` and `npr.apiKey` in your local `gradle.properties` OR `$HOME/.gradle/gradle.properties`:

```properties
npr.apiUrl=https://registry.nextflow.io/api
npr.apiKey=your-api-key
```

**Option 3: Using environment variables**
Export environment variables in your shell:

```bash
export NPR_API_URL=https://registry.nextflow.io/api
export NPR_API_KEY=your-api-key
```

The configuration precedence is: explicit values → project properties → environment variables → defaults.

### Available Tasks

This will add some useful tasks to your Gradle build:
* `assemble` - Compile the Nextflow plugin code and assemble it into a zip file
* `installPlugin` - Copy the assembled plugin into your local Nextflow plugins dir
* `releasePlugin` - Release the assembled plugin to the plugin registry (always available)
* `releasePluginToRegistry` - Release the plugin to the configured registry (always available)

You should also ensure that your project's `settings.gradle` declares the plugin name, eg:
```gradle
rootProject.name = '<YOUR-PLUGIN-NAME>'
```

### Default Dependencies

By default, the plugin automatically adds several dependencies required for Nextflow plugin development. You can disable this behavior by setting `useDefaultDependencies = false` in your plugin configuration.

When `useDefaultDependencies` is `true` (default), the following dependencies are automatically added:

**Compile-time dependencies (compileOnly):**
- `io.nextflow:nextflow:${nextflowVersion}` - Core Nextflow classes
- `org.slf4j:slf4j-api:1.7.10` - Logging API
- `org.pf4j:pf4j:3.4.1` - Plugin framework

**Test dependencies (testImplementation):**
- `org.apache.groovy:groovy` - Groovy language support
- `io.nextflow:nextflow:${nextflowVersion}` - Nextflow runtime for testing
- `org.spockframework:spock-core:2.3-groovy-4.0` - Spock testing framework
- `org.spockframework:spock-junit4:2.3-groovy-4.0` - Spock JUnit4 integration

**Test runtime dependencies:**
- `org.objenesis:objenesis:3.4` - Object instantiation library
- `net.bytebuddy:byte-buddy:1.14.17` - Code generation library

**Test fixtures:**
- `testFixtures("io.nextflow:nextflow:${nextflowVersion}")` - Nextflow test utilities
- `testFixtures("io.nextflow:nf-commons:${nextflowVersion}")` - Common test utilities

To disable default dependencies and manage them manually:

```gradle
nextflowPlugin {
    nextflowVersion = '25.04.0'
    provider = 'Example Inc'
    className = 'com.example.ExamplePlugin'
    useDefaultDependencies = false
    
    // Add your own dependencies in the dependencies block
}

dependencies {
    // Your custom dependencies here
    compileOnly 'io.nextflow:nextflow:25.04.0'
    // etc...
}
```

## Migrating an existing Nextflow plugin

Follow these steps to migrate an existing Nextflow plugin:

1. If your project uses a `plugins` dir, move its `src` dir to the project root
2. Make sure your plugin sources are now in `src/main/groovy` or `src/main/java`
3. Replace any gradle build files with the configuration described above

See https://github.com/nextflow-io/nf-hello/pull/21 for an example

## To build and test the plugin locally

This section is only relevant if you want to make changes to this Gradle plugin itself, and isn't 
needed for developing Nextflow plugins.

1. Checkout this project and install it to your local maven repo: `./gradlew publishToMavenLocal`
2. In your Nextflow plugin project, add this to the `settings.gradle`:
```gradle
pluginManagement {
    repositories {
      mavenLocal()
      gradlePluginPortal()
    }
}
```
3. Apply the configuration, as described above


## Development of Gradle plugin for Nextflow 

To release this plugin include the [Gradle plugins registry](https://plugins.gradle.org) API keys in your `gradle.properties`. 

Then use this command:

```
./gradlew publishPlugins
```
