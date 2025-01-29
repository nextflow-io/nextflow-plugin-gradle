package io.nextflow.gradle.github

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.PackageScope

/**
 * Represents the data in plugins.json index file
 */
@PackageScope
class PluginsIndex {
    private final List<PluginMeta> plugins

    private PluginsIndex(List<PluginMeta> plugins) {
        this.plugins = plugins
    }

    PluginMeta getPlugin(String id) {
        this.plugins.find { p -> p.id == id }
    }

    def add(PluginMeta plugin) {
        this.plugins.add(plugin)
    }

    String toJson() {
        new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
            .toJson(plugins) + '\n'
    }

    static PluginsIndex fromJson(String json) {
        final type = new TypeToken<ArrayList<PluginMeta>>() {}.getType()
        final data = new Gson().fromJson(json, type)
        new PluginsIndex(data)
    }
}

@PackageScope
@CompileStatic
@EqualsAndHashCode
class PluginMeta {
    String id
    String name
    String provider
    String description
    List<PluginRelease> releases
}

@PackageScope
@CompileStatic
@EqualsAndHashCode
class PluginRelease {
    String version
    String url
    String date
    String sha512sum
    String requires
}