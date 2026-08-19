# Split Spring Boot And Spring Cloud Release Lines

## Goal

Replace the single `jfoundry-spring-dependencies` release line with two explicit, mutually
exclusive Spring runtime lines: a Spring Boot-only line based on Spring Boot 4.1.0 and a Spring Cloud
line based on Spring Boot 4.0.7, Spring Cloud 2025.1.2, and Spring Cloud Alibaba 2025.1.0.0.

## Context

The current Spring runtime BOM imports Spring Boot, Spring Cloud, and Spring Cloud Alibaba in one
published artifact. Spring Cloud 2025.1.2 remains aligned with the Spring Boot 4.0.x line while the
Boot-only baseline is moving to Spring Boot 4.1.0. Keeping both platform families in one BOM would
make the default Boot upgrade implicitly select an unverified Cloud combination.

The repository does not need a compatibility alias for `jfoundry-spring-dependencies`. Existing
consumers must choose the new BOM and, when applicable, the new Cloud parent explicitly.

## Published Artifacts

### Spring Boot line

- Rename `jfoundry-spring-dependencies` to `jfoundry-spring-boot-dependencies`.
- Manage Spring Boot `4.1.0` and the foundation-managed shared component families.
- Do not import Spring Cloud or Spring Cloud Alibaba BOMs.
- Keep `jfoundry-spring-boot-parent` as the Boot-only consumer parent, upgrade its inherited
  `spring-boot-starter-parent` to `4.1.0`, and import `jfoundry-spring-boot-dependencies` before
  `jfoundry-dependencies`.

### Spring Cloud line

- Add `jfoundry-spring-cloud-dependencies`.
- Manage Spring Boot `4.0.7`, Spring Cloud `2025.1.2`, Spring Cloud Alibaba `2025.1.0.0`, and the
  foundation-managed shared component families.
- Add `jfoundry-spring-cloud-parent`, inheriting `spring-boot-starter-parent:4.0.7` and importing
  `jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies`.
- The Cloud parent is the supported entry point for applications that use Spring Cloud or Spring
  Cloud Alibaba. It is not a second parent that can be combined with `jfoundry-spring-boot-parent`.

Both runtime BOMs remain standalone published POMs. Neither imports `jfoundry-dependencies`; the
consumer imports the runtime BOM first and the framework-neutral BOM second so platform constraints
take precedence while JFoundry module versions remain available.

## Repository Changes

- Replace the old Spring BOM module in the root reactor and source tree with the Boot BOM module.
- Add the Cloud BOM and Cloud parent modules to the reactor and release metadata.
- Remove active source, test fixture, consumer verification, supply-chain, skill-reference, README,
  release compatibility, and English/Chinese documentation references to
  `jfoundry-spring-dependencies`. Preserve historical references in the one-time `1.0.0` remediation
  workflow and its audit documentation because they describe an already-published coordinate.
- Add contract tests for both BOMs and both parents, including exact platform coordinates, import
  order, and the absence of Cloud imports from the Boot-only BOM.
- Extend Consumer POM verification to install and resolve both direct BOM combinations and both
  parent consumers. The fixtures must reject a parent or BOM with the wrong platform coordinates or
  reversed runtime/core import order.
- Update supply-chain and Dependabot protection references to cover the renamed Boot BOM and the new
  Cloud BOM/parent coordinates.

## Version Ownership

Foundation remains the sole owner of shared third-party component families such as JobRunr,
MyBatis-Plus, Redisson, and jMolecules Integrations. The two Spring runtime BOMs may import the
Foundation BOM, but they must not redeclare those family versions. Spring platform coordinates are
owned by the corresponding runtime BOM line.

## Documentation And Compatibility

Documentation must present two mutually exclusive choices:

| Application needs | Parent | Runtime BOM | Platform line |
|---|---|---|---|
| Spring Boot without Spring Cloud | `jfoundry-spring-boot-parent` | `jfoundry-spring-boot-dependencies` | Boot 4.1.0 |
| Spring Cloud or Spring Cloud Alibaba | `jfoundry-spring-cloud-parent` | `jfoundry-spring-cloud-dependencies` | Boot 4.0.7, Cloud 2025.1.2, Cloud Alibaba 2025.1.0.0 |

The getting-started guide, Spring Boot implementation guide, release compatibility matrix, and
matching Chinese documentation must state that applications select exactly one line. Examples that
use a different Maven parent must show the matching runtime BOM before `jfoundry-dependencies`.

This is an intentional breaking coordinate change. The old Spring BOM and the old single-parent
behavior are removed rather than aliased. Applications using the old coordinate must migrate to the
Boot-only or Cloud-specific coordinate before consuming the next release.

## Verification

- Run the BOM and parent contract tests with Maven 3.9.
- Run the consumer POM regression and Maven 3.9/Maven 4 resolution checks for both lines.
- Run the Java 25 unit and package matrix.
- Run Spring Boot 4.1.0 JVM and Native consumer checks.
- Run a versionless Cloud/Alibaba consumer-resolution check against the Cloud line; do not claim
  Boot 4.1 + Cloud 2025.1.2 compatibility.
- Run repository documentation, workflow, and release metadata verification scripts.

## Non-Goals

- Do not provide a compatibility alias for `jfoundry-spring-dependencies`.
- Do not change Spring Cloud's upstream release cadence or attempt to backport Cloud support to Boot
  4.1.0.
- Do not change framework-neutral module APIs or runtime behavior unrelated to dependency management.
