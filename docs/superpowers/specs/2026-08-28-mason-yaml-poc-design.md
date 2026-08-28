# Mason YAML Proof-of-Concept Design

## Objective

Determine whether JFoundry can treat Mason YAML POMs as build source while preserving the Maven 4 build, Consumer POM, Maven 3 consumer, release metadata, and Maven Central publication contracts that currently depend on XML POMs.

The proof of concept is intentionally reversible. It runs on the `codex/mason-poc` branch, does not publish to Maven Central, and does not change the production release workflow until the experiment has produced sufficient evidence.

## Upstream Context

Maven Central is the default repository used by Maven, but it is operated by Sonatype rather than the Apache Maven project. Apache Maven and Sonatype therefore ship independently.

Apache Maven tracks Maven 4 Central readiness in [MNG-8584](https://github.com/apache/maven/issues/10647). The issue remains open with `priority:major`. Its relevant findings are:

- Maven 4 can run the standard Maven Deploy Plugin, but the new Central Portal accepts a complete deployment bundle rather than Maven Deploy Plugin's module-by-module file upload.
- Sonatype Central Publishing Maven Plugin normally acts as a build extension and replaces the standard deploy behavior. That extension path is not reliable with Maven 4 release candidates.
- The documented workaround is to disable standard deploy and bind `central-publishing:publish` explicitly to the `deploy` phase without enabling the plugin as an extension.
- Sonatype's plugin has no official public source repository, and Sonatype has not committed to Maven 4 support before Maven 4 reaches a final release.

JFoundry currently uses Maven 4.0.0-rc-6 for normal builds and Consumer POM generation, but Maven 3.9.16 for the protected Central deployment because the extension path is unreliable under RC6. This remains the largest external risk for a Mason migration because Mason itself only runs on Maven 4.

## Scope

The proof of concept converts only a representative cross-section of the real reactor:

| Project | Why it is included |
| --- | --- |
| Root `jfoundry-parent` and reactor | Exercises Maven configuration, module discovery, plugin management, release profile, and mixed XML/YAML children. |
| `jfoundry-domain` | Exercises a normal JAR with inherited dependency management and test dependencies. |
| `jfoundry-spring-boot-starter` | Exercises nested plugin executions, Enforcer configuration, empty Javadoc attachment, and Maven property expressions. |
| `jfoundry-helidon-dependencies` | Exercises an independent published BOM, imported BOMs, release metadata, custom SCM inheritance attributes, GPG configuration, and Central publishing configuration. |

All other reactor POMs remain XML during this experiment. The Spring Boot parent fixture under `src/test/resources` also remains XML because it is consumer test input, not a reactor build descriptor.

The PoC does not:

- convert all 122 reactor POMs;
- publish any release or snapshot to Sonatype;
- modify public Java APIs, dependencies, module boundaries, or runtime behavior;
- claim IDE support beyond recording observed behavior;
- make YAML migration a requirement for `main`.

## Source Format

YAML is the only Mason dialect used. TOML and HOCON have upstream limitations, while JSON5 offers no material benefit for this repository.

The PoC uses Maven model version `4.0.0` unless a Maven 4.1-only feature is required. Keeping model version 4.0.0 isolates Mason syntax from Maven 4.1 model compatibility and keeps generated Consumer POMs readable by Maven 3 and Gradle.

Mason is registered once in `.mvn/extensions.xml`. The source of truth for converted projects is `pom.yaml`; generated XML files are temporary verification or publication inputs and are never committed.

## Architecture

### Mixed Reactor

Maven 4.0.0-rc-6 starts from the root YAML descriptor and loads converted YAML children through Mason. Unconverted children continue to use `pom.xml`. Parent resolution and relative paths must work in both directions:

- XML children must resolve the YAML root parent.
- YAML children must resolve XML intermediate parents where applicable.
- Reactor selection with `-pl` and dependency closure with `-am` must remain unchanged.

Any need to keep a committed XML shadow POM is a PoC failure because it would restore dual sources of truth.

### Model Equivalence

A verification script creates an isolated XML baseline from the branch point and compares it with the YAML candidate. Comparisons use generated Maven models rather than textual XML/YAML similarity.

The comparison covers:

- project coordinates, packaging, parent, modules, and properties;
- dependencies and dependency management including type, classifier, scope, and optional state;
- build plugins, executions, goals, phases, and nested configuration;
- profiles and activation;
- name, description, URL, licenses, developers, and SCM metadata;
- installed Consumer POMs for representative JAR, starter, BOM, and parent artifacts.

Machine-specific absolute paths, formatting, comments, and element ordering that Maven defines as unordered are normalized before comparison. Any other difference fails the PoC until it is explained and explicitly accepted.

### Maven 3 Publication Bridge

Maven 3 cannot read Mason YAML. The fallback publication experiment therefore generates temporary Maven 4.0 XML POMs from the YAML model into a disposable checkout before invoking Maven 3.9.16.

The generated POM set must:

- cover the entire reactor selected for deployment, not only the four converted modules;
- contain no machine-specific absolute paths;
- preserve the source model rather than embedding a developer workstation's effective build directories;
- pass existing release metadata and Consumer POM verification;
- remain untracked and be deleted with the temporary checkout.

`help:effective-pom` is acceptable as an exploratory input, but not automatically accepted as the publication bridge because its output includes inherited defaults and absolute paths. The implementation must either normalize it safely or use a more faithful Maven model writer/conversion mechanism.

### Maven 4 Central Publishing Experiment

A dedicated PoC profile tests Apache Maven's MNG-8584 workaround:

- Central Publishing Maven Plugin is configured without `<extensions>true>`.
- Standard Maven deploy is skipped.
- `central-publishing:publish` is explicitly bound to `deploy`.
- The plugin uses the same release metadata, signing outputs, Consumer POM settings, and reactor artifacts as the current release profile.

The experiment must never contact the real Central Portal. It uses fake credentials and a loopback HTTP endpoint or a plugin-supported offline/deferred output mode. Verification inspects the resulting bundle and confirms that it contains correctly named POMs, JARs, source archives, Javadoc archives, signatures, and checksums for the selected projects. If plugin behavior cannot be tested without a real upload, the PoC records that limitation instead of weakening the no-upload guard.

The production `.github/workflows/release.yml` remains on Maven 3.9.16 throughout the PoC.

## Safety And Failure Handling

- All repository work occurs on `codex/mason-poc` created from current `origin/main`.
- No Central username, password, token, or GPG private material is written to the repository.
- Test publication endpoints must resolve to loopback before Maven starts. A non-loopback endpoint is a hard failure.
- Temporary generated POMs, repositories, settings files, keys, bundles, and logs live under a validated temporary directory.
- A cleanup failure must be reported; it must not hide the primary build or verification failure.
- Existing release and supply-chain scripts remain authoritative. PoC helpers extend their evidence rather than bypassing their checks.

## Verification

The PoC is successful only if all applicable checks pass:

1. Mason YAML parsing succeeds on Java 25 and Maven 4.0.0-rc-6.
2. The mixed 122-project reactor passes `validate`.
3. `jfoundry-domain` tests pass from its YAML POM.
4. The Spring Boot starter retains its Enforcer and Javadoc plugin behavior.
5. The Helidon BOM retains dependency management, project metadata, SCM attributes, and release profile behavior.
6. Normalized Maven models match the XML baseline for all four converted projects.
7. A clean Maven 4 install produces Consumer POMs accepted by the existing Maven 3.9/Maven 4 checks.
8. Existing release POM metadata and release workflow tests pass.
9. The Maven 3 publication bridge can run at least through a local file-repository deploy without reading YAML directly.
10. The explicit Maven 4 Central publish binding produces a structurally valid local bundle without contacting Sonatype, or the experiment records a reproducible plugin limitation.
11. `scripts/verify-ci-matrix.sh` passes with Java 25 before the PoC is proposed for broader adoption.

Native Image and middleware integration matrices are not required because the experiment does not change runtime code or resolved dependency versions. They become required if model comparison reveals dependency or plugin-resolution drift.

## Decision Criteria

Proceed to broader migration only when:

- YAML is the sole committed source for converted projects;
- model and Consumer POM equivalence is automated;
- Maven 3 publication can consume deterministic generated XML, or the explicit Maven 4 Central path is proven against a safe Sonatype test facility;
- the publication POMs retain all Central metadata and custom Maven inheritance attributes;
- developers retain acceptable IDE navigation and Maven project import behavior;
- the migration and verification cost is justified for the remaining POMs.

Stop or defer migration when:

- any converted project requires a committed XML shadow POM;
- generated publication POMs contain environment-specific paths or semantic drift;
- Mason 0.3.0 cannot represent a required plugin configuration or XML attribute correctly;
- the unpublished fixes on Mason `main` are required but no released version is available;
- release verification becomes materially weaker than the current Maven 3/Maven 4 contract.

## Deliverables

The PoC branch will contain:

- YAML POMs for the four representative projects;
- Mason extension configuration;
- deterministic model-equivalence and generated-POM verification helpers with tests;
- a no-upload Maven 4 Central bundle experiment;
- documentation of commands, results, upstream blockers, and the final adopt/defer decision.

Production-wide migration, release workflow replacement, and removal of Maven 3.9.16 are separate follow-up decisions.
