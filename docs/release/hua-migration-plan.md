# JFoundry Identity Migration

Status: **in progress**. Revised on 2026-11-20. The product name stays `jfoundry`.

This replaces the earlier proposal to rename the repository, modules, artifacts, packages, configuration, and tables to `hua`. That product rename is rejected. `hua` is the organization name, not the framework name.

Maven 4.0.0 final is still unpublished. The wrapper remains Maven `4.0.0-rc-7`. The baseline bump stays blocked and is not part of the identity change.

## Target identity

| Concern | Target | Progress |
| --- | --- | --- |
| GitHub organization | `huahill` | Done. `xfoundries/jfoundry` redirects to `huahill/jfoundry`. |
| GitHub repository | `jfoundry` | Kept. Do not rename the repository to `hua`. |
| Maven groupId | `io.github.huahill` | Implemented locally. Not pushed or merged. |
| Artifact and module names | `jfoundry-*` | Kept. |
| Java package root | `io.huahill.jfoundry.*` | Implemented locally. Not pushed or merged. |
| Configuration, environment variables, and tables | `jfoundry.*`, `JFOUNDRY_*`, `jfoundry_*` | Kept. |
| Reactor version | `1.8.0-SNAPSHOT` | Kept. Do not reset the line to `0.1.0`. |
| Publishing server and GitHub environment | `jfoundry` | Kept. |

Maven coordinates and Java packages use different roots. `io.github.huahill:jfoundry-*` is the Central coordinate. `io.huahill.jfoundry.*` is the Java API. The `io.github.huahill` namespace already publishes `vadmin-*`; this framework must not publish `hua-*` artifacts there.

## Progress

### Done

- [x] Transfer the repository to the `huahill` organization without renaming it.
- [x] Confirm `io.github.huahill` is a verified Central namespace. It already contains `vadmin-*`.
- [x] Change the reactor `groupId` from `io.github.xfoundries` to `io.github.huahill` on local branch `codex/huahill-jfoundry-coordinates`, commit `7ae7773d`.
- [x] Move Java sources and imports from `org.jfoundry` to `io.huahill.jfoundry` in that same commit, including ArchUnit rules, metadata, and release lookups for the current reactor.
- [x] Point project URL and SCM metadata at `https://github.com/huahill/jfoundry`.
- [x] Leave already published `io.github.xfoundries:jfoundry-*` artifacts unchanged. The one-time `jfoundry-spring-boot-parent:1.0.0` remediation still targets the old coordinate.
- [x] Run `mvn validate`, plus the domain module, ArchUnit, and Spring Boot parent POM tests: 190 tests passed. Full `mvn test` and `scripts/verify-ci-matrix.sh` have not been run.

### Not started

- [ ] Push `codex/huahill-jfoundry-coordinates` and review it in its own pull request. This pull request tracks the decision only.
- [ ] Select Maven 4.0.0 final, record it in `docs/release/compatibility.md`, and update the wrapper and CI together.
- [ ] Decide whether to publish relocation POMs for the old `io.github.xfoundries:jfoundry-*` coordinates. Relocation cannot preserve Java package compatibility, and existing releases must not be overwritten.
- [ ] Confirm the existing `jfoundry` publishing environment still has Central and GPG credentials that can publish `io.github.huahill`.

### Rejected

- [x] Rename the GitHub repository from `jfoundry` to `hua`.
- [x] Rename modules and artifacts from `jfoundry-*` to `hua-*`.
- [x] Use `io.huahill.*` without the `jfoundry` segment, or move source trees to `org/huahill`.
- [x] Rename product configuration, environment variables, resource paths, or persisted table names.
- [x] Reset the first migrated release to `0.1.0`.

## Verification before merge of the identity change

- [x] `git diff --check`.
- [x] `mvn validate` on the current Maven `4.0.0-rc-7` baseline.
- [x] Focused domain, ArchUnit, and Spring Boot parent POM tests.
- [ ] Release-POM, Consumer POM, dependency-boundary, workflow, and supply-chain verification scripts.
- [ ] `mvn test` and, when Java 25 is available, `scripts/verify-ci-matrix.sh`.
- [ ] Inspect produced POMs, BOMs, source JARs, Javadocs, and service metadata for a stale `io.github.xfoundries` or `org.jfoundry` identity outside historical remediation.
- [ ] Integrate the identity change with GitHub's Squash and merge after the server-side `Merge gate` passes.

## Explicit non-goals

- Do not delete or rewrite existing Git tags, GitHub releases, or Maven Central artifacts.
- Do not publish compatibility aliases for the renamed Java packages.
- Do not redesign module boundaries or runtime capability decomposition.
- Do not rename third-party coordinates, upstream technology names, or the `jMolecules` project.
