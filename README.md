# Nextflow plugin Gradle plugin

This is a [Gradle](https://gradle.org/) plugin for building [Nextflow](https://www.nextflow.io/) plugins.

It sets up default dependencies required for integration with Nextflow, and adds some custom gradle tasks
to help build and publish your Nextflow plugin.

## Configuration

Apply and configure this plugin in your `build.gradle` file, for example:

```gradle
plugins {
    id 'io.nextflow.nextflow-plugin' version '0.0.1-alpha'
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

## Usage

This plugin is currently in development and not yet published to the Gradle plugin portal.

To try it out, follow these steps:

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
3. If your project uses a `plugins` dir, move the `src` dir to the project root
4. Apply the configuration, as described above

See https://github.com/nextflow-io/nf-hello/pull/21 for an example
