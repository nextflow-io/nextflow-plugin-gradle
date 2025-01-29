package io.nextflow.gradle.github

import com.google.gson.Gson
import io.nextflow.gradle.NextflowPluginConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task to update the plugins.json index file in a github repo.
 */
class UpdateJsonIndexTask extends DefaultTask {
    @InputFile
    final RegularFileProperty jsonFile

    UpdateJsonIndexTask() {
        group = 'Nextflow Plugin'
        description = 'Publish the plugin metadata to the Nextflow plugins index'

        final buildDir = project.layout.buildDirectory.get()
        jsonFile = project.objects.fileProperty()
        jsonFile.convention(project.provider {
            buildDir.file("distributions/${project.name}-${project.version}-meta.json")
        })
    }

    @TaskAction
    def run() {
        final plugin = project.extensions.getByType(NextflowPluginConfig)
        final config = plugin.publishing.github
        final indexUrl = plugin.publishing.indexUrl

        // github client
        def (org, repo, branch, filename) = new URI(indexUrl).path.tokenize('/')
        final github = new GithubClient(owner: org, repo: repo, branch: branch,
            userName: config.userName, email: config.email, authToken: config.authToken)

        // download the existing plugins index
        def index = PluginsIndex.fromJson(github.getContent(filename))

        // parse the meta.json file for this plugin release
        def meta = new PluginMeta(id: project.name, provider: plugin.provider, releases: [])
        def release = new Gson().fromJson(project.file(jsonFile).text, PluginRelease)

        // merge it into the index
        if (updateIndex(index, meta, release)) {
            // push changes to central index
            logger.quiet("Pushing merged index to $indexUrl")
            github.pushChange(filename, index.toJson(), "${meta.id} version ${release.version}")
        }
    }

    private static boolean updateIndex(PluginsIndex index, PluginMeta meta, PluginRelease release) {
        def updated = false

        // get or add the entry for this plugin id
        def plugin = index.getPlugin(meta.id)
        if (!plugin) {
            index.add(meta)
            plugin = meta
        }

        // look for an existing release with this version
        def existing = plugin.releases.find { r -> r.version == release.version }
        if (!existing) {
            // add release if doesn't exist
            plugin.releases.add(release)
            updated = true
        } else if (existing.sha512sum != release.sha512sum) {
            // error if release exists but checksums different
            throw new GradleException("""
                Plugin ${meta.id}@${release.version} already exists in index:
                - index sha512sum: ${existing.sha512sum}
                - repo sha512sum : ${release.sha512sum}
            """)
        }
        updated
    }
}
