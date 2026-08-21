# Support Policy

## Release Contract

JFoundry uses Semantic Versioning beginning with `1.0.0`.

- Public APIs and configuration properties published by a released JFoundry artifact are stable
  within the same major line unless they are explicitly documented as limited support.
- A backward-incompatible API, configuration, SQL-template, or behavior change requires the next
  major version. A compatible deprecation remains available for at least one subsequent minor
  release and is described in release notes.
- Patch releases contain compatible fixes, dependency updates, documentation corrections, and
  security remediation. They do not intentionally change framework semantics.
- The currently maintained support line is the latest released `1.x` version. Security reports are
  handled according to [SECURITY.md](../../SECURITY.md).

The first stable release has no earlier stable artifact from which to run an upgrade. Before the
next stable release, CI must compare public APIs against `1.0.0` and the maintained expense-approval
demo variants must upgrade from a published `1.0.0` artifact.

## Platform Scope

The supported platform versions and Native Image evidence are maintained in the
[Compatibility Matrix](compatibility.md). The supported scope is deliberately bounded:

- Spring Boot 4.0.x, Quarkus 3.37.3, and Helidon MP 4.5.2 are supported only through their documented
  JFoundry assemblies.
- Spring Native Image is validated for the base runtime plus MyBatis-Plus/PostgreSQL,
  Redisson/Redis, and JobRunr/PostgreSQL paths.
- Helidon Native supports the documented CDI/Web consumer path. Helidon Native JTA and JPA are not
  supported until the upstream limitation documented in the compatibility matrix is resolved.
- Other databases, brokers, ORMs, deployment targets, and downstream dependency graphs require
  consumer-owned acceptance tests.

JFoundry does not supply authentication, authorization, an identity provider, a universal execution
context, production monitoring backends, backup/recovery, capacity guarantees, or disaster recovery.
Those remain application and operational responsibilities.

## Release Candidate Acceptance

A release candidate is an immutable, signed pre-release artifact, not a substitute for a stable
production release. Promotion to a stable release requires all of the following:

1. The candidate tag points to committed non-SNAPSHOT source and the complete CI matrix succeeds for
   that commit.
2. The release profile produces signed JARs, source JARs, Javadoc JARs, and an aggregate CycloneDX
   SBOM; Central accepts a staged deployment.
3. Maintainers inspect the staged POM metadata, signatures, BOM import, checksums, SBOM, and GitHub
   provenance evidence.
4. Each maintained expense-approval demo branch consumes the candidate as an external dependency and
   completes its own documented acceptance path.
5. No unapproved High or Critical dependency alert remains open, and release notes list supported
   scope, known limitations, and upgrade impact.

The stable GitHub Release is created only after Central publication succeeds. The
`Prepare next SNAPSHOT` workflow then opens a separate pull request for the next development
`-SNAPSHOT` version; it never changes `main` directly.
