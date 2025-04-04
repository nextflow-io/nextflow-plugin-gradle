# Nextflow plugin Gradle plugin

> [!WARNING]
> This plugin is in early active development, please use `nextflow-io/nf-hello` for production usage for now.

This is a [Gradle](https://gradle.org/) plugin for building [Nextflow](https://www.nextflow.io/) plugins.

It sets up default dependencies required for integration with Nextflow, and adds some custom gradle tasks
to help build and publish your Nextflow plugin.

## Configuration

Apply and configure this plugin in your `build.gradle` file, for example:

```gradle
plugins {
    id 'io.nextflow.nextflow-plugin' version '0.0.1-alpha3'
}

dependencies {
    // (optional) put any library dependencies here
}

// plugin version
version = '0.0.1'

nextflowPlugin {
    // minimum nextflow version
    nextflowVersion = '24.11.0-edge'

    provider = 'example-inc'
    className = 'com.example.ExamplePlugin'
    extensionPoints = [
        'com.example.ExampleObserver',
        'com.example.ExampleFunctions'
    ]

    publishing {
        github {
            repository = 'example-inc/nf-example'
            userName = project.findProperty('github_username')
            authToken = project.findProperty('github_access_token')
            email = project.findProperty('github_commit_email')

            indexUrl = 'https://github.com/nextflow-io/plugins/main/plugins.json'
        }
    }
}
```

This will add some useful tasks to your Gradle build:
* `assemble` - compile the Nextflow plugin code and assemble it into a zip file
* `installPlugin` - copy the assembled plugin into your local Nextflow plugins dir
* `releasePlugin` - published the assembled plugin to a Github repository, and update the central plugins.json index repository

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
