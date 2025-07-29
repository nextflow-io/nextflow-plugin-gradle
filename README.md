# Gradle plugin for Nextflow

> [!WARNING]
> This plugin is in early active development, please use `nextflow-io/nf-hello` for production usage for now.

This is a [Gradle](https://gradle.org/) plugin for building [Nextflow](https://www.nextflow.io/) plugins.

It sets up default dependencies required for integration with Nextflow, and adds some custom gradle tasks
to help build and publish your Nextflow plugin.

## Configuration

Apply and configure this plugin in your `build.gradle` file, for example:

```gradle
plugins {
    id 'io.nextflow.nextflow-plugin' version '0.0.1-alpha6'
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

    publishing {
        registry {
            // Registry URL (optional, defaults to plugin-registry.seqera.io/api)
            url = 'https://plugin-registry.seqera.io/api'
            
            // API key for authentication (required)
            apiKey = project.findProperty('npr.apiKey')
        }
    }
}
```

### Registry Configuration

The registry publishing configuration supports multiple ways to provide the URL and API key:

#### Registry URL
The registry URL can be configured via (in order of priority):
1. `nextflowPlugin.publishing.registry.url` in build.gradle
2. Gradle property: `-Pnpr.url=https://your-registry.com/api`
3. Environment variable: `NPR_URL=https://your-registry.com/api`
4. Default: `https://plugin-registry.seqera.io/api`

#### API Key
The API key can be configured via (in order of priority):
1. `nextflowPlugin.publishing.registry.apiKey` in build.gradle
2. Gradle property: `-Pnpr.apiKey=your-api-key`
3. Environment variable: `NPR_API_KEY=your-api-key`

**Note:** The API key is required for publishing. If none is provided, the plugin will show an error with configuration instructions.

#### Example Configurations

Using gradle.properties:
```properties
npr.url=https://my-custom-registry.com/api
npr.apiKey=your-secret-api-key
```

Using environment variables:
```bash
export NPR_URL=https://my-custom-registry.com/api
export NPR_API_KEY=your-secret-api-key
./gradlew publishPlugin
```

This will add some useful tasks to your Gradle build:
* `assemble` - compile the Nextflow plugin code and assemble it into a zip file
* `installPlugin` - copy the assembled plugin into your local Nextflow plugins dir
* `publishPlugin` - publish the assembled plugin to the plugin registry

You should also ensure that your project's `settings.gradle` declares the plugin name, eg:
```gradle
rootProject.name = '<YOUR-PLUGIN-NAME>'
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
