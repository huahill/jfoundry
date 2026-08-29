# Mason YAML Proof of Concept

## Decision

The repository-wide Mason YAML proof of concept is technically successful. All
122 reactor projects use `pom.yaml`; the only source-tree `pom.xml` is the
intentional XML consumer fixture under `jfoundry-spring-boot-parent`.

This branch adopts Mason as the committed reactor model and supports Maven 4
only. Published artifacts still contain ordinary XML Consumer POM metadata, so
downstream consumers need neither Mason nor Maven 4. Maven 3 is not a supported
source-build, CI, or release runtime.

Production Central publication remains deliberately blocked until a Maven 4
final release and a verified Maven 4 Central Publishing path are available.
The release workflow fails fast while either condition is absent.

## Source Layout

Mason is registered as `eu.maveniverse.maven.mason:mason:0.3.0` in
`.mvn/extensions.xml`. Every reactor project commits only `pom.yaml`.

The conversion and source checks are provided by:

- `scripts/support/convert-xml-pom-to-mason-yaml.rb` for one descriptor;
- `scripts/convert-mason-reactor.sh` for the complete reactor;
- `scripts/verify-mason-poc.sh` for source-layout invariants;
- `scripts/verify-mason-model-equivalence.sh` for effective-model comparison.

The source verifier recursively follows every `modules` declaration, requires
exactly 122 reachable YAML projects, rejects XML shadow POMs and XML parent
paths, and checks Mason registration and known Mason 0.3.0 syntax limitations.

## Environment And Fidelity

Evidence was collected with Java 25, Maven `4.0.0-rc-6`, Mason `0.3.0`, and
Docker Desktop `29.7.2`. Aggregate effective models for all 122 projects were
equivalent after normalizing YAML/XML parent filenames. Maven warning output was
also equivalent after path and location normalization.

Mason 0.3.0 infers the wrong XML item name for Maven Compiler's
`annotationProcessorPaths/path` shape. The affected Quarkus deployment projects
use an explicit `path` wrapper, protected by converter fixtures.

## Quarkus Model Handoff

Quarkus tests consume Maven's resolved application model through
`quarkus-maven-plugin:generate-code-tests`; the forked Surefire JVM cannot load
Mason's Maven core extension. `skipSourceGeneration` follows `skipTests`, so
package and release verification do not resolve deployment artifacts too early.
The diagnosis is recorded in [Quarkus issue #56270](https://github.com/quarkusio/quarkus/issues/56270).

## Release And Consumer Verification

The Maven 4 wrapper performs clean reactor `install` and `verify` operations.
`scripts/verify-consumer-pom.sh` checks flattened child POMs, independent BOMs,
Spring Boot parent inheritance, and versionless Spring Cloud resolution using
Maven 4 only. Standard XML Consumer POMs remain the published compatibility
boundary for Maven 3, Gradle, and other consumers, but JFoundry does not run a
Maven 3 compatibility claim for its source tree.

The release workflow retains metadata, SBOM, signature, provenance, and Central
visibility checks. It invokes Maven 4's normal `deploy` lifecycle with the
Central Publishing extension once readiness is explicitly enabled through the
`MAVEN_CENTRAL_MAVEN4_READY` repository variable. It does not generate or
publish a separate XML tree.

The current wrapper is Maven `4.0.0-rc-6`, so the guard intentionally fails.
Maven 4 final and a real Central test/release verification are required before
enabling the variable.

## Version Workflow

`versions-maven-plugin:versions:set` does not support Mason YAML. The
post-release workflow uses `scripts/set-mason-reactor-version.rb`, which updates
classified project, parent, and `jfoundry.version` references while preserving
comments, ordering, quoting, and timestamps.

## Runtime Matrix

The complete YAML reactor passed the Java 25 unit/package matrix, Spring,
Quarkus, and Helidon JVM middleware checks, and the supported Native Image
consumer checks. The JobRunr Native Image failure is reproduced on the XML
baseline and remains an upstream JobRunr/GraalVM issue, not a Mason regression.

## Remaining Risks

- Maven 4 Central readiness is tracked by [MNG-8584](https://github.com/apache/maven/issues/10647).
- Mason 0.3.0 is an additional build extension and requires explicit review of
  nonstandard plugin collection mappings.
- IDE import/navigation and Dependabot YAML update behavior require separate
  validation.

## Adoption Boundary

Keep this branch independent until Maven 4 final and Central publication are
validated. Before merging, enable the readiness guard only after a real Maven 4
release successfully publishes and Central exposes the exact coordinates.
