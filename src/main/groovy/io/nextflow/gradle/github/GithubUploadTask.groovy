package io.nextflow.gradle.github

import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task to upload assembled plugin and metadata file to a Github release.
 */
class GithubUploadTask extends DefaultTask {
    @InputFile
    final RegularFileProperty zipFile
    @InputFile
    final RegularFileProperty jsonFile

    GithubUploadTask() {
        group = 'Nextflow Plugin'
        description = 'Publish the assembled plugin to a Github repository'

        final buildDir = project.layout.buildDirectory.get()
        zipFile = project.objects.fileProperty()
        zipFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}.zip")
        })
        jsonFile = project.objects.fileProperty()
        jsonFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}-meta.json")
        })
    }

    @TaskAction
    def run() {
        final version = project.version.toString()
        final plugin = project.extensions.getByType(NextflowPluginConfig)
        final config = plugin.publishing.github

        // github client
        def (owner, repo) = config.repositoryParts()
        final github = new GithubClient(authToken: config.authToken, userName: config.userName,
            owner: owner, repo: repo)

        // create the github release, if it doesn't already exist
        def release = github.getRelease(version)
        if (!release) {
            logger.quiet("Creating release ${config.repository} ${version}")
            release = github.createRelease(version)
        }

        // upload files to github release
        final uploader = new Uploader(github: github, config: config)
        uploader.uploadAsset(release, project.file(zipFile), 'application/zip')
        uploader.uploadAsset(release, project.file(jsonFile), 'application/json')
    }

    class Uploader {
        GithubClient github
        GithubPublishConfig config

        def uploadAsset(Map release, File file, String mimeType) {
            if (!config.overwrite && github.getReleaseAsset(release, file.name)) {
                logger.quiet("Already exists on release, skipping: '${file.name}'")
            } else {
                logger.quiet("Uploading ${file.name}")
                github.uploadReleaseAsset(release, file, mimeType)
            }
        }
    }
}
