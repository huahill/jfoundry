# Hua Identity Migration Plan

Status: **pending**. Implementation must wait until the selected Maven 4 final release is available and the wrapper/CI baseline has been validated against it.

This document records the complete migration from the current `xfoundries/jfoundry` identity to the `huahill/hua` identity. It is a breaking migration, not a compatibility alias or a repository copy.

## Approved target identity

| Concern | Target |
| --- | --- |
| GitHub organization | `huahill` |
| GitHub repository | `hua` |
| Maven namespace/groupId | `io.github.huahill` |
| Java package root | `io.huahill.*` |
| Project-owned module/artifact prefix | `hua-` |
| First release after migration | `0.1.0` |

Maven coordinates and Java packages intentionally use different roots. `io.github.huahill` is tied to the GitHub-verifiable Central namespace; `io.huahill.*` keeps the Java API under the organization's reverse-domain namespace.

## Prerequisites outside the repository

- [ ] Transfer `xfoundries/jfoundry` to the `huahill` organization and rename it to `hua` through GitHub's transfer flow. Do not create a new repository and push a copy.
- [ ] Verify that the transfer preserves stars, forks, issues, pull requests, wiki, projects, Actions history, tags, releases, release assets, and the old URL redirect.
- [ ] Confirm that the destination repository is not an unrelated non-empty repository. Back up any existing destination content before taking an irreversible action.
- [ ] Register and verify the `io.github.huahill` namespace in Sonatype Central Portal.
- [ ] Confirm the migrated repository's publishing environment still has Central and GPG credentials. Keep the internal publishing server/environment identifier stable during this migration unless operations explicitly changes it.

## Repository changes

### 1. Maven 4 baseline

- [ ] Select the exact Maven 4 final release and record it in `docs/release/compatibility.md`.
- [ ] Update `.mvn/wrapper/`, `mvnw`, `mvnw.cmd`, and all CI Maven setup together.
- [ ] Pass `mvn validate`, Maven 4 model checks, Consumer POM checks, and wrapper checks before identity edits.
- [ ] Do not proceed while only a release candidate or mixed Maven 4 baseline is available.

### 2. Maven coordinates and project metadata

- [ ] Change project-owned `io.github.xfoundries` coordinates to `io.github.huahill` in the root POM, every child POM, BOM, fixture, script, and generated publication check.
- [ ] Rename the root parent artifact `jfoundry-parent` to `hua-parent`.
- [ ] Set the first migrated reactor version to `0.1.0`.
- [ ] Change project URL, SCM connection, developer connection, release workflow URLs, Central lookup paths, release evidence, and documentation to `https://github.com/huahill/hua`.
- [ ] Keep old `io.github.xfoundries:*` coordinates only where needed for historical documentation or explicitly tested relocation POMs. Never overwrite or delete existing releases.

### 3. Complete module and artifact rename

- [ ] Generate and review an old-path → new-path and old-artifactId → new-artifactId inventory before renaming anything.
- [ ] Rename source grouping directories: `jfoundry-boms/` → `hua-boms/`, `jfoundry-core/` → `hua-core/`, and `jfoundry-runtime/` → `hua-runtime/`.
- [ ] Rename every project-owned child directory and Maven artifact beginning with `jfoundry-` to `hua-`, including foundation/module/runtime BOMs, architecture, domain, application, infrastructure, Jakarta, Spring, Quarkus, Helidon, parent, starter, runtime, deployment, and integration-test modules.
- [ ] Update reactor membership, all relative paths, parent references, dependency declarations, BOM imports, dependency-management keys, profiles, plugin configuration, test fixtures, scripts, IDE metadata, and workflow paths.
- [ ] Preserve capability names and runtime decomposition; this is a vocabulary change, not a module-boundary redesign.
- [ ] Rename the module architecture Drawio/SVG assets and update all references, then regenerate the SVG from Drawio.

### 4. Product-facing configuration and resources

- [ ] Inventory and review every product-owned `jfoundry.*` property, `JFOUNDRY_*` environment variable, endpoint, metric/log category, resource path, and default table name.
- [ ] Rename those identifiers to `hua` equivalents where they are part of the public product identity; leave third-party properties and protocol names unchanged.
- [ ] Update Spring configuration metadata, Native Image reachability metadata, service-loader resources, runtime probes, examples, and test fixtures together.
- [ ] Treat SQL resource paths and persisted table names as data migrations. Provide explicit `ALTER TABLE`/migration guidance instead of silently changing defaults and losing existing data.
- [ ] Add checks that reject stale project-owned identifiers outside historical migration documentation.

### 5. Java package migration

- [ ] Rename Java source/test directory trees `.../org/jfoundry/...` to `.../org/huahill/...`.
- [ ] Change declarations and imports from `org.jfoundry.*` to `io.huahill.*`, preserving the existing subpackage layout and type names.
- [ ] Update ArchUnit rules, package convention tests, Service Loader entries, reflection configuration, Native Image metadata, logging categories, and all package-sensitive fixtures.
- [ ] Add an inventory check that fails if project-owned source/resources still reference `org.jfoundry`.

### 6. Documentation and compatibility guidance

- [ ] Update `README.md`, `README_ZH.md`, all English/Chinese integration and implementation guides, release docs, examples, badges, and repository links.
- [ ] Document the full mapping `io.github.xfoundries:*` + `org.jfoundry.*` + `jfoundry-*` → `io.github.huahill:*` + `io.huahill.*` + `hua-*`.
- [ ] Document that coordinate, artifact, package, configuration, endpoint, resource, and persisted-identifier changes are incompatible and require consumer/data migration.
- [ ] Decide and test whether old-coordinate relocation POMs will be published. Relocation cannot preserve Java package compatibility.
- [ ] Update `AGENTS.md` and the local maintenance guidance if renamed module paths or diagram paths are referenced there. Do not rename third-party names or upstream project identifiers.

## Verification and delivery

- [ ] Run `git diff --check` and documentation verification.
- [ ] Run `mvn validate`, focused module/ArchUnit tests, then `mvn test` on the Maven 4 final baseline.
- [ ] Run the affected release-POM, Consumer POM, dependency-boundary, workflow, supply-chain, and Maven model verification scripts.
- [ ] Run `scripts/verify-ci-matrix.sh` when Java 25 is available, including Spring, Quarkus, Helidon, JVM middleware, and Native Image stages required by the changed modules.
- [ ] Inspect produced POMs, BOMs, source JARs, Javadocs, service metadata, and publication paths for stale identity values.
- [ ] Commit on a short-lived `codex/<scope>` branch based on `origin/main`, push with an explicit upstream, open a pull request, and integrate only after the server-side `Merge gate` passes using Rebase and merge.

## Explicit non-goals

- Do not delete or rewrite existing Git tags, GitHub releases, or Maven Central artifacts.
- Do not silently introduce compatibility aliases for renamed Java packages or persisted tables.
- Do not redesign Onion/Hexagonal module boundaries or runtime capability decomposition as part of the rename.
- Do not rename third-party coordinates, upstream technology names, or the `jMolecules` project.
