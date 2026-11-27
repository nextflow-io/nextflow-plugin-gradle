# Namespaced plugin IDs

- Authors: Jorge Ejarque
- Status: proposed
- Deciders: Ben Sherman, Phil Ewels, Paolo Di Tommaso
- Date: 2026-11-12
- Tags: plugins, registry

Technical Story: https://github.com/nextflow-io/nextflow/issues/8077, https://github.com/seqeralabs/nextflow-registry/pull/451 (link and freeze, option B2), https://github.com/seqeralabs/nextflow-registry/pull/451#issuecomment-7130306191 (`Plugin-Namespace` proposal)

## Summary

Support plugin IDs of the form `<namespace>/<name>` (e.g. `myorg/nf-foo`), next to the existing unscoped IDs (e.g. `nf-amazon`). Reuse the namespace model that modules already use in the Nextflow registry.

- The MANIFEST (`META-INF/MANIFEST.MF` inside the plugin zip) keeps the plain name in `Plugin-Id`. A new attribute, `Plugin-Namespace`, holds the namespace.
- An existing unscoped plugin can move into a namespace. It keeps one registry record and one list of releases, and the registry accepts both IDs (`nf-foo` and `myorg/nf-foo`).
- Older Nextflow ignores `Plugin-Namespace` and loads a moved plugin as `nf-foo`, as today. Newer Nextflow loads the same zip as `myorg/nf-foo`.
- The registry tells Nextflow the namespaced ID of a moved plugin. Newer Nextflow changes every plain ID of that plugin to the namespaced ID, so a run loads the plugin only once.
- Once the registry turns on enforcement (a setting that makes namespaces required), an existing plugin must move before it can publish a new version, and new plugins must be namespaced.

This decision needs changes in three repositories: Nextflow, the plugin registry and the Gradle plugin (`nextflow-plugin-gradle`). Each repository has a copy of this ADR. This copy gives the full detail for the Gradle plugin and a short summary for the other two.

## Problem Statement

Plugin IDs are global and flat. Two organisations cannot publish a plugin with the same name. Nobody owns a name: the first author to claim it gets it. Private registries make this worse. Each one needs its own names, and an organisation cannot reserve its names in the public registry.

Modules already solved this with scoped names (`nf-core/fastqc`, see the module system ADR in the Nextflow repository). Plugins should follow the same model.

Today a `/` in a plugin ID fails in several places:

- Nextflow uses the ID in file paths (lock file, plugins store, local plugin root, offline repository).
- The registry rejects a `/` in the ID: `PluginId` validation fails on claim, publish, metadata and download.
- The Gradle plugin takes the ID from `project.name`, which cannot contain `/`.

Second problem: existing plugins. With namespaced IDs alone, an author who wants a namespace must publish the plugin again under a new ID. The new ID has no releases, no download stats and no link to the old plugin, and every pipeline must change the ID by hand. The registry alone cannot make `nf-foo` serve the releases of `myorg/nf-foo`:

- pf4j, the plugin library in Nextflow, registers a plugin under the `Plugin-Id` in its zip MANIFEST. It cannot map one plugin ID to another.
- Nextflow downloads a plugin by the ID it asked for, then calls `startPlugin(id)` with that ID (`PluginUpdater.load0`).
- In an experiment, a zip with `Plugin-Id: myorg-nf-foo` was served as `nf-foo`, and the reverse. Both failed with `PluginNotFoundException: Plugin '<requested id>' not found`.

So a zip can only be loaded under the ID in its MANIFEST, unless Nextflow changes that ID when it reads the MANIFEST.

## Goals or Decision Drivers

- An organisation can own a namespace and publish plugins under it, in the public registry and in private registries.
- The same plugin name can exist in different namespaces, and both can be used in the same run.
- An existing plugin can move into a namespace and keep one record: one list of releases, one set of stats and one page. The author publishes each release once.
- Pipelines that use a plain ID keep working, including pinned versions such as `nf-foo@1.2.0`. Nextflow versions without namespace support also keep working. Both keep getting new releases of moved plugins, with no change on their side.
- A new namespaced plugin used with an old component fails with a clear error.
- Fast adoption: moving is cheap for authors, and the registry can make namespaces required.
- One naming model for plugins and modules: same ownership, same API shape.

## Non-goals

- Authentication for private registries. Nextflow sends no token when it fetches plugin metadata or downloads a plugin. This is needed for private registries but is a separate decision.
- Moving a plugin to a different name (`nf-foo` to `myorg/nf-bar`), or from one namespace to another.
- An audit trail of who moved a plugin.
- Changing how Nextflow combines several registries. It already works with `registry.url`. The Nextflow ADR says how a moved plugin fits in.

## Considered Options

There are two questions:

- **A.** Should a plugin ID include a namespace? (A1 to A3)
- **B.** If yes, how does the zip carry the namespace, and how does an existing plugin move into a namespace? (B1 to B5)

### A. Plugin ID model

#### A1. Keep flat IDs, use a naming convention

Plugin authors add a prefix to the name, e.g. `myorg-nf-foo`.

- **Pro:** No change in Nextflow, the registry or the Gradle plugin.
- **Con:** No ownership. Anyone can claim `myorg-*`.
- **Con:** Different from modules.

#### A2. Namespace only in the registry

The registry stores plugins under a namespace, but the plugin ID stays flat (`nf-foo`). The registry maps the flat ID to the namespace.

- **Pro:** No change in Nextflow file paths.
- **Con:** A pipeline cannot say which `nf-foo` it wants. Two plugins with the same name cannot be used in the same run, because pf4j keys plugins by ID.

#### A3. Namespaced plugin IDs, same model as modules (chosen)

The namespace is part of the plugin ID in config, `Plugin-Dependencies`, the registry and the local store.

- **Pro:** Same model as modules. Clear ownership and no clashes.
- **Pro:** pf4j needs no change. It parses `Plugin-Dependencies: org/foo@>=1.0` correctly.
- **Con:** Changes in Nextflow, the registry and the Gradle plugin.
- **Con:** Older Nextflow versions cannot use new namespaced plugins.

### B. Namespace in the zip, and moving existing plugins

#### B1. Namespace inside `Plugin-Id`

The MANIFEST has `Plugin-Id: myorg/nf-foo`.

- **Pro:** The ID is the same in the MANIFEST, in Nextflow and in the registry.
- **Con:** A zip loads under one ID only. A moved plugin cannot serve `nf-foo` to pipelines and older Nextflow, so moving needs a second record (B2) or a second copy of each zip (B3).

#### B2. Link, then freeze

`nf-foo` and `myorg/nf-foo` stay two plugins, linked in the registry. Both accept releases until the author stops releases for `nf-foo` (the freeze).

- **Pro:** No change to how Nextflow loads plugins, beyond a warning and a duplicate check.
- **Pro:** The author decides how long to support older Nextflow versions.
- **Con:** Two records and two release lists. While linked, the author builds and publishes each release two times.
- **Con:** After the freeze, pipelines that use `nf-foo` and older Nextflow versions get no new releases.
- **Con:** A run could ask for both IDs. Nextflow must detect this case.
- **Con:** Moving is optional and costly, so most plugins would stay unscoped and both models would stay forever.

#### B3. Duplicate storage

As B2, and the registry stores a copy of each `myorg/nf-foo` release with `Plugin-Id: nf-foo`, served as an `nf-foo` release.

- **Pro:** No Nextflow change. The author publishes once.
- **Con:** The registry changes the author's zip. The copy has a different checksum from the file the author built.
- **Con:** Each release needs two OCI artifacts (the container-registry objects where the registry stores zips). The registry must download, unpack and repack the zip. Today it only streams the upload.
- **Con:** A run could ask for both IDs. Nextflow must detect this case.

#### B4. Unique names, namespace for ownership only

A plugin name is unique in the whole registry. The namespace only says who owns the plugin. `myorg/nf-foo` in a config is another way to write `nf-foo`.

- **Pro:** Old pipelines and old Nextflow versions keep working. A plugin cannot be loaded twice, because it has one ID.
- **Con:** Two organisations cannot use the same name, an organisation cannot reserve its names, and private registries can reuse names from the public one. These are the problems that namespaces solve (A3).

#### B5. `Plugin-Namespace` in the MANIFEST, one record with two IDs (chosen)

`Plugin-Id` stays the plain name and `Plugin-Namespace` holds the namespace. A move sets the namespace on the existing record. Newer Nextflow builds the ID from both attributes and maps plain IDs of moved plugins to the namespaced one.

- **Pro:** One record, one release list, one stored zip per release, no link or freeze states.
- **Pro:** Older Nextflow loads the same zip as `nf-foo` and keeps getting new releases.
- **Pro:** The namespace stays part of the ID on newer Nextflow.
- **Pro:** pf4j needs no change. Nextflow subclasses the pf4j descriptor finder, whose methods are `protected`.
- **Con:** The ID in the MANIFEST and the ID on newer Nextflow differ.
- **Con:** The largest Nextflow change: the ID mapping, the rewrite of dependencies, and the saved mapping for offline runs.
- **Con:** Nextflow must get the namespaced ID of every moved plugin from the registry before it loads anything. Without it, a plugin can be loaded twice.

## Solution

Options A3 and B5.

### Plugin ID

- Format: `[<namespace>/]<name>`, with at most one `/`.
- Unscoped IDs are not changed. `nf-foo` and `myorg/nf-foo` are different plugins, unless `nf-foo` moved into `myorg`.
- The namespace is optional until the registry turns enforcement on.
- The registry owns the naming rules for a namespaced ID. The namespace follows the registry namespace rule (`^[a-z0-9]([a-z0-9-]{1,62}[a-z0-9])?$`). The name follows the current plugin name rule. Unscoped IDs keep their current rules, so no existing plugin is refused.
- The ID is used as is in config (`plugins { id 'myorg/nf-foo@1.0.0' }`), in `include from 'plugin/myorg/nf-foo'`, in `nextflow plugin myorg/nf-foo:<cmd>` and in `Plugin-Dependencies`.
- `nf-core` is a protected namespace: nobody can publish `nf-core/...` plugins.
- `nextflow-io` and `seqera` are not protected. They are already claimed in production. Core and open-source plugins go in `nextflow-io`. Seqera plugins, such as `xpack`, go in `seqera`.

### Plugin MANIFEST

```
Plugin-Id: nf-foo
Plugin-Namespace: myorg
```

| Plugin | `Plugin-Id` | `Plugin-Namespace` | `Plugin-Dependencies` |
|---|---|---|---|
| Unscoped, not moved | `nf-foo` | none | plain IDs |
| Moved, release built before the move | `nf-foo` | none | plain IDs |
| Moved, release built after the move | `nf-foo` | `myorg`, or none for patch releases built from older branches | plain IDs while the author supports older Nextflow |
| New namespaced plugin (never had a plain name) | `nf-foo` | `myorg` | namespaced IDs, or the plain ID of a moved plugin |

- `Plugin-Id` is always the plain name and never contains `/`. pf4j reads only this attribute, so older Nextflow is not affected by the new one.
- A moved plugin keeps plain dependencies while it supports older Nextflow: older Nextflow cannot resolve a namespaced dependency.
- A new namespaced plugin depends on namespaced IDs. A plain ID works only for a plugin that has a plain name (unscoped or moved).

### Registry hint: `canonicalId`

`canonicalId` is a new optional field in the `GET /api/v1/plugins/dependencies` response. It gives the namespaced ID of a moved plugin. The registry fills it and Nextflow needs it.

- The response has one entry per requested ID. The `id` field is the requested ID, so older Nextflow finds the entry under the ID it asked for.
- Each entry of a moved plugin has `canonicalId: "myorg/nf-foo"`, whichever ID was requested.
- When one request has both IDs (for example `myorg/nf-foo` from the config and `nf-foo` from a dependency), the response has both entries.
- The plain name is the part of `canonicalId` after the `/`, because a move keeps the name. So one field maps the plain name to the namespaced ID, and back.
- New namespaced plugins and unscoped plugins have no `canonicalId`.
- `/dependencies` already returns the dependencies of the requested plugins (up to 5 levels), so Nextflow gets every hint in one call.
- `canonicalId` is a plain optional field in the existing API version. Nextflow decodes the response with Gson, which ignores unknown fields.
- Nextflow accepts a `canonicalId` only if it is a valid namespaced ID. For a plain request it must have the same name. For a namespaced request it must be the same ID.

### Nextflow (summary)

- Nextflow checks only the structure of a namespaced ID (at most one `/`, no empty part, no `.` or `..`, no `~`), not the naming rules.
- It uses a flat name with `~` in place of `/` for every file name: the plugins store folder (`$NXF_HOME/plugins/<namespace>~<name>-<version>`) and the lock file.
- It builds the ID of a loaded plugin from `Plugin-Namespace` and `Plugin-Id`, or from the store folder name for a release built without `Plugin-Namespace`.
- It maps the plain name of a moved plugin to the namespaced ID, from the `canonicalId` hint. It maps requested IDs and `Plugin-Dependencies`, so a run loads a moved plugin once. It warns when the config uses the plain name.
- It stores a moved plugin once and links the other name to it. Offline runs read the map from these links.
- Core plugin IDs change to `nextflow-io/...` in the release with namespace support.
- When a registry refuses a namespaced ID, Nextflow shows the registry response as a warning.
- With several registries, the first registry that reports a move sets the namespace of the plain name. The plain releases of registries that did not move the plugin are added to it.

See the ADR in the Nextflow repository.

### Registry (summary)

- Reuses its namespace model for modules and pipelines: ID validation with an optional `<namespace>/` prefix, a `namespace_id` column on plugins, publish permission through namespace membership, `%2F`-encoded IDs in API routes, download file name `<name>-<version>.zip`, and a `plugins/` prefix in OCI storage.
- Namespaced plugins are left out of the legacy `plugins.json`. Moved plugins stay in it under their plain name.
- On publish, it checks `Plugin-Id` against the plugin name and `Plugin-Namespace` against the namespace. A moved plugin may publish without `Plugin-Namespace`, for patch releases built from older branches.
- It refuses a namespaced ID when the request's `nextflowVersion` is older than a configured minimum: the first Nextflow release that reads `Plugin-Namespace`. For such a client, `/dependencies` also leaves out namespaced plugins found in the dependency walk, and the releases that depend on one.
- Move: the owner of an unscoped plugin who is a member of the target namespace, or an admin, moves it from the plugin page. The record keeps its ID and gets the namespace. Both IDs resolve to it, and `/dependencies` returns the `canonicalId` hint.
- Revoked namespace: the namespaced ID returns 410, and the plain name of a moved plugin keeps working.
- Enforcement is a registry flag (`REGISTRY_PLUGINS_NAMESPACE_ENFORCED`), off by default. When on, unscoped plugins that did not move cannot publish, and new unscoped plugins cannot be claimed.

See the ADR in the registry repository.

### Gradle plugin

- A new optional setting: `nextflowPlugin { namespace = 'myorg' }`.
- The plugin ID is `<namespace>/<project.name>`, or `project.name` when no namespace is set.
- `validate()` checks `namespace` with the registry namespace rule (`^[a-z0-9]([a-z0-9-]{1,62}[a-z0-9])?$`). The current check on `project.name` does not change.
- The MANIFEST has `Plugin-Id: <project.name>`, and `Plugin-Namespace: <namespace>` when a namespace is set. `Plugin-Id` never contains `/`.
- The plugin ID is used for the release request (`id` in the JSON body of `POST v1/plugins/release`) and the log messages.
- The local install task (`installPlugin`) copies the plugin to `plugins/<namespace>~<project.name>-<version>`, the same layout as the Nextflow store.
- The zip stays `<project.name>-<version>.zip`. The registry and Nextflow expect this name.
- `requirePlugins` is copied as is into `Plugin-Dependencies`, so namespaced dependencies already work. A moved plugin keeps plain dependencies while it supports older Nextflow.
- Moving a plugin into a namespace is done in the registry. After the move, the author sets `namespace` in the build. A patch release for older Nextflow lines can still be built without `namespace`.
- With an old registry, the release request fails with the registry message "Plugin id 'myorg/nf-foo' is invalid". No extra code is needed.

### Compatibility

New namespaced plugins:

| Nextflow | Registry | Unscoped plugin | Namespaced plugin |
|---|---|---|---|
| old | old | works | not possible |
| new | old | works | fails: clear message from Nextflow, shown as a warning, then "Cannot find version" |
| old | new | works | fails: the registry message (`nextflowVersion` check) is shown as a warning, then "Cannot find version" |
| new | new | works | works |

| Gradle plugin | Registry | Unscoped plugin | Namespaced plugin |
|---|---|---|---|
| old | any | works | not possible (no `namespace` setting) |
| new | old | works | fails: the registry refuses the ID |
| new | new | works | works |

Moved plugins:

| Nextflow | Asks for `nf-foo` (moved) | Asks for `myorg/nf-foo` |
|---|---|---|
| reads `plugins.json` only | releases as today, loaded as `nf-foo` | not possible |
| `/dependencies`, no namespace support | all releases, loaded as `nf-foo` | not possible |
| namespace support | loaded as `myorg/nf-foo`, with the notice | loaded as `myorg/nf-foo` |

What older Nextflow does not get:

- new namespaced plugins, which have no plain name;
- releases of a moved plugin that depend on a namespaced ID. The registry leaves them out of `/dependencies` for older Nextflow, so it picks the newest release it can load. It also logs a warning at publish.

Older Nextflow sharing a plugin store with newer Nextflow, offline, with a version range:

- It reads every folder in the store and keys it by `Plugin-Id`. A folder `org1~nf-foo-2.0.0` of a new namespaced plugin shows as a release of `nf-foo`. Older Nextflow then fails with "is not a regular file".
- A broken link (for example after the user deletes the folder it points to) makes it fail with "Cannot find the manifest path".
- Pinned versions are not affected.

### Worked example: core plugins (`nf-amazon` to `nextflow-io/nf-amazon`)

1. **Registry:** an admin moves `nf-amazon` to `nextflow-io`. The record keeps its ID and releases.
2. **Nextflow:** the release with namespace support uses `nextflow-io/nf-amazon` and builds the core plugins with `Plugin-Namespace: nextflow-io`.

Effect:

- Older Nextflow keeps asking for `nf-amazon` and gets every release, including new ones.
- Newer Nextflow asks for `nextflow-io/nf-amazon`, or for `nf-amazon` with the `canonicalId` hint in the answer.
- Patch releases for older Nextflow lines are published to the same record without `Plugin-Namespace`.
- Plugins that depend on `nf-amazon` (for example `xpack-amzn`) keep a plain dependency and need no new release.

The xpack plugins need a change before they move to `seqera`: they use the plugin ID as the license product code. See the ADR in the Nextflow repository.

### Rollout

1. Release the registry and the Gradle plugin, with enforcement off. Set `namespaced-min-nextflow-version` to 26.12.0-edge, the first release planned with namespace support. No Nextflow release is that new yet, so this refuses nothing that works today. If namespace support misses that release, change the value.
2. Move the core plugins to `nextflow-io`. This must come before step 3, because that release asks for `nextflow-io/...` IDs. Older Nextflow keeps using the plain names.
3. Nextflow release with namespace support, including the core plugin ID change.
4. Move the Seqera plugins to `seqera`. Apply the xpack product-code change before the first build with `Plugin-Namespace`.
5. Tell plugin authors when enforcement will be turned on, with a how-to for claiming a namespace and moving a plugin.
6. Turn enforcement on.

## Prototype results

A prototype in Nextflow tested the Nextflow part of B5, with the registry hint simulated. A moved plugin loads once with the hint. Without the hint, it can load twice or fail to resolve a plain dependency, so the `canonicalId` hint is required. Released Nextflow 26.10.4 loads a zip with `Plugin-Namespace` with no warning. See the ADR in the Nextflow repository for details.

## Rationale

- Plugins and modules live in the same registry. One naming and ownership model is simpler for users and for the registry.
- Putting the namespace in the ID (instead of only in the registry) lets a pipeline choose the exact plugin, and lets two plugins with the same name run together.
- One record and one zip per release remove the cost that B2 and B3 put on authors and on the registry.
- `Plugin-Id` stays plain, so older Nextflow loads every release of a moved plugin without change. Because of this, the registry can make moving required.
- The price is in Nextflow: the ID mapping, which needs the registry hint, and the saved map for offline runs.

## Consequences

- Pipelines that use new namespaced plugins need a minimum Nextflow version: the first release that reads `Plugin-Namespace`. They should set `manifest.nextflowVersion`.
- With an old registry, only the namespaced plugin fails. Nextflow then retries with one plugin per request, so the unscoped plugins of the same run still resolve.
- Newer Nextflow needs the `canonicalId` hint before it loads a plugin. A registry or index without it can load a moved plugin twice.
- Offline runs need a store filled online by a Nextflow version with namespace support.
- Older Nextflow sharing a store with newer Nextflow can fail offline with a version range (see Compatibility).
- The ID shown by Nextflow (`myorg/nf-foo`) differs from `Plugin-Id` in the MANIFEST (`nf-foo`).
- Authors of moved plugins keep plain dependencies while they support older Nextflow.
- Once enforcement is on, no new unscoped plugins are added.

## Open decisions

- When to turn enforcement on, and how long after the Nextflow release.
- How an admin allows a single release that enforcement would refuse.
- Whether an admin can move a plugin whose author does not respond.
- Docs: ID format, the `Plugin-Namespace` attribute, store layout, minimum Nextflow version, the migration guide, and offline store preparation.
