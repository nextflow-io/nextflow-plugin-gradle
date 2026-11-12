# Namespaced plugin IDs

- Authors: Jorge Ejarque
- Status: proposed
- Deciders: Ben Sherman, Phil Ewels, Paolo Di Tommaso
- Date: 2026-11-12
- Tags: plugins, registry

Technical Story: https://github.com/nextflow-io/nextflow/issues/8077 (Nextflow issue)

## Summary

Support plugin IDs of the form `<namespace>/<name>` (e.g. `myorg/nf-foo`), next to the existing unscoped IDs (e.g. `nf-amazon`). Reuse the namespace model that modules already use in the Nextflow registry.

This decision needs changes in three repositories: Nextflow, the plugin registry and the Gradle plugin (`nextflow-plugin-gradle`). Each repository has a copy of this ADR. This copy gives the full detail for the Gradle plugin and a short summary for the other two.

## Problem Statement

Plugin IDs are global and flat. Two organisations cannot publish a plugin with the same name, and there is no ownership of a name beyond "first to claim it". Private registries make this worse: each one needs its own names, and an organisation cannot reserve its names in the public registry.

Modules already solved this with scoped names (`nf-core/fastqc`, see the module system ADR in the Nextflow repository). Plugins should follow the same model.

Today a `/` in a plugin ID fails in several places:

- Nextflow uses the ID in file paths (lock file, plugins store, local plugin root, offline repository).
- The registry rejects a `/` in the ID: `PluginId` validation fails on claim, publish, metadata and download.
- The Gradle plugin takes the ID from `project.name`, which cannot contain `/`.

## Goals or Decision Drivers

- An organisation can own a namespace and publish plugins under it, in the public registry and in private registries.
- The same plugin name can exist in different namespaces, and both can be used in the same run.
- Existing unscoped plugins keep working without changes, for plugin authors and for users.
- Old and new versions of Nextflow, the registry and the Gradle plugin work together for unscoped plugins. A namespaced plugin used with an old component fails with a clear error.
- One naming model for plugins and modules: same ownership, same API shape.

## Non-goals

- Authentication for private registries. Nextflow sends no token when it fetches plugin metadata or downloads a plugin. This is needed for private registries but is a separate decision.
- Moving existing plugins into namespaces (e.g. `nf-amazon` to `nextflow/nf-amazon`).
- Making the namespace required for new plugins.
- Lookup across several registries. It already works with `registry.url` and does not change.

## Considered Options

### 1. Keep flat IDs, use a naming convention

Plugin authors add a prefix to the name, e.g. `myorg-nf-foo`.

- **Pro:** No change in Nextflow, the registry or the Gradle plugin.
- **Con:** No ownership. Anyone can claim `myorg-*`.
- **Con:** Different from modules.

### 2. Namespace only in the registry

The registry stores plugins under a namespace, but the plugin ID stays flat (`nf-foo`). The registry maps the flat ID to the namespace.

- **Pro:** No change in Nextflow file paths.
- **Con:** A pipeline cannot say which `nf-foo` it wants. Two plugins with the same name cannot be used in the same run, because pf4j keys plugins by ID.
- **Con:** The ID in the MANIFEST and the ID in the registry differ, which is confusing.

### 3. Namespaced plugin IDs, same model as modules

The namespace is part of the plugin ID everywhere: config, MANIFEST, registry and local store.

- **Pro:** Same model as modules. Clear ownership and no clashes.
- **Pro:** pf4j needs no change. It only checks that the ID is not empty, and it parses `Plugin-Dependencies: org/foo@>=1.0` correctly.
- **Con:** Changes in Nextflow, the registry and the Gradle plugin.
- **Con:** Older Nextflow versions cannot use namespaced plugins.

## Solution

Option 3.

### Plugin ID

- Format: `[<namespace>/]<name>`, with at most one `/`.
- Unscoped IDs are not changed. `nf-foo` and `myorg/nf-foo` are different plugins.
- The namespace is optional.
- Naming rules for a namespaced ID are owned by the registry: the namespace follows the registry namespace rule (`^[a-z0-9]([a-z0-9-]{1,62}[a-z0-9])?$`), and the name follows the current plugin name rule. Unscoped IDs keep their current rules in each component, so no existing plugin is refused.
- The ID is used as is in config (`plugins { id 'myorg/nf-foo@1.0.0' }`), in `include from 'plugin/myorg/nf-foo'`, in `nextflow plugin myorg/nf-foo:<cmd>` and in `Plugin-Dependencies`.
- The `nf-core` namespace is protected in the registry, so nobody can publish `nf-core/...` plugins. The `nextflow` namespace is not protected. Seqera claims it as a normal namespace ( same as modules or pipelines ).

### Nextflow (summary)

Nextflow checks only the structure of the ID (at most one `/`, no empty part, no `.` or `..`), not the naming rules. It stores namespaced plugins at `$NXF_HOME/plugins/<namespace>/<name>-<version>`, and uses a flat name with `~` for the lock file and the local plugin root symlink. Offline and embedded mode scan one level deeper. When a registry refuses a namespaced ID, the final error shows the registry response. See the ADR in the Nextflow repository.

### Registry (summary)

The registry reuses its namespace model for modules and pipelines: ID validation with an optional `<namespace>/` prefix, a `namespace_id` column on plugins, publish permission through namespace membership, `%2F`-encoded IDs in API routes, download file name `<name>-<version>.zip`, a `plugins/` prefix in OCI storage, namespaced plugins left out of the legacy `plugins.json`, and namespace revoke, delete and "My plugins" support. It refuses a namespaced ID when the request's `nextflowVersion` is older than a configured minimum, with a clear message. See the ADR in the registry repository.

### Gradle plugin

- A new optional setting: `nextflowPlugin { namespace = 'myorg' }`.
- The plugin ID is `<namespace>/<project.name>`, or `project.name` when no namespace is set.
- `validate()` checks `namespace` with the registry namespace rule (`^[a-z0-9]([a-z0-9-]{1,62}[a-z0-9])?$`). The current check on `project.name` does not change.
- The ID is used for the MANIFEST `Plugin-Id`, the release request (`id` in the JSON body of `POST v1/plugins/release`) and the log messages.
- The local install task (`installPlugin`) copies the plugin to `plugins/<namespace>/<project.name>-<version>`, the same layout as the Nextflow store.
- The zip stays `<project.name>-<version>.zip`. The registry and Nextflow expect this name.
- `requirePlugins` is copied as is into `Plugin-Dependencies`, so namespaced dependencies already work.
- With an old registry, the release request fails with the registry message "Plugin id 'myorg/nf-foo' is invalid". No extra code is needed.

### Compatibility

| Nextflow | Registry | Unscoped plugin | Namespaced plugin |
|---|---|---|---|
| old | old | works | not possible |
| new | old | works | fails: clear message from Nextflow |
| old | new | works | fails: the registry message (`nextflowVersion` check) is shown as a warning, then "Cannot find version" |
| new | new | works | works |

| Gradle plugin | Registry | Unscoped plugin | Namespaced plugin |
|---|---|---|---|
| old | any | works | not possible (no `namespace` setting) |
| new | old | works | fails: the registry refuses the ID |
| new | new | works | works |

Nextflow versions that read the legacy `plugins.json` index never see namespaced plugins.

### Rollout

1. Registry and Gradle plugin, in any order.
2. Nextflow. It can be done in parallel and tested with a local `plugins.json` index, because it changes nothing for unscoped plugins.
3. Docs: ID format, store layout, minimum Nextflow version.

## Rationale

- Plugins and modules live in the same registry. One naming and ownership model is simpler for users and for the registry.
- Putting the namespace in the ID (instead of only in the registry) lets a pipeline choose the exact plugin, and lets two plugins with the same name run together.
- pf4j does not need changes. All the Nextflow work is in places where the ID becomes a file name.
- In the Gradle plugin, a separate `namespace` setting keeps `project.name` as the only source of the name, so there is one way to set it.

## Consequences

- Pipelines that use namespaced plugins need a minimum Nextflow version. They should set `manifest.nextflowVersion`.
- With an old registry, only the namespaced plugin fails. Nextflow asks again one plugin per request, so unscoped plugins of the same run still resolve.
- Old Nextflow versions show the registry message only as a warning. This cannot be fixed in versions already released.
- Unscoped plugins, their store paths and older Nextflow versions are not affected.
