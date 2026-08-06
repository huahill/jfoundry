# Spring Boot Parent And Runtime Directory Design

## Goal

Provide one consumer-facing `jfoundry-spring-boot-parent` POM. A Spring Boot application uses it as
its only Maven parent and can declare Spring Boot and JFoundry dependencies without versions. The
parent must inherit the JFoundry Spring Boot baseline from `spring-boot-starter-parent:4.0.7`.

Shorten the physical runtime source group from `jfoundry-runtime-integrations` to
`jfoundry-runtime`. This is a repository-path change only: published artifact coordinates remain
unchanged.

## Parent POM

Create `jfoundry-boms/jfoundry-spring-boot-parent/pom.xml` as a direct root reactor module and a
published POM with these coordinates:

```xml
<groupId>io.github.xfoundries</groupId>
<artifactId>jfoundry-spring-boot-parent</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>
```

It directly inherits `org.springframework.boot:spring-boot-starter-parent:4.0.7`. Its dependency
management imports, in this order, `jfoundry-dependencies` and `jfoundry-spring-dependencies` at
the same `${project.version}`. This preserves the current split of responsibilities:

- `spring-boot-starter-parent` supplies Spring Boot's dependency and build-plugin defaults.
- `jfoundry-dependencies` manages public JFoundry module versions.
- `jfoundry-spring-dependencies` manages the Spring, Spring Cloud, and Spring Cloud Alibaba
  ecosystems chosen by JFoundry.

The duplicated Spring Boot BOM import is version-aligned at 4.0.7 and intentionally harmless. The
new parent owns the fixed Spring Boot Parent version because a Maven parent version is resolved
before child properties are available. The version must be updated with the Spring Boot baseline in
`jfoundry-spring-dependencies`.

The new POM is consumer-facing rather than an internal build parent. It declares JFoundry project
metadata and release publication configuration required to publish the POM independently. It does
not add any runtime dependency, starter, plugin execution, or Java source.

## Consumer Contract

A Spring Boot consumer replaces its parent and both existing BOM imports with:

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>1.0.0</version>
</parent>
```

The application still explicitly selects `jfoundry-spring-boot-starter` and any capability
starters. The parent must not cause Outbox, Inbox, broker clients, persistence adapters, JobRunr,
or other optional capability dependencies to become implicit.

Existing applications may continue to import the two JFoundry BOMs directly. This is an additive
entry point, not a removal or coordinate migration.

## Runtime Directory Rename

Move the root directory `jfoundry-runtime-integrations/` to `jfoundry-runtime/` with its Spring,
Quarkus, and Helidon children unchanged. Update every root module path, nested relative parent
path, Maven invocation path, CI workflow exclusion, verification script, tests that invoke Maven,
and documentation reference.

No `artifactId`, Java package, public API, module name, or capability behavior changes. The
renamed group does not have an aggregate POM or a published `jfoundry-runtime-integrations`
artifact, so users' Maven coordinates are unaffected.

## Documentation

Update English and Chinese integration guidance to make `jfoundry-spring-boot-parent` the
recommended Spring Boot entry point. Explain that it is the application's sole Maven parent and
replaces the pair of JFoundry BOM imports. Retain a short alternative for projects that cannot use
the JFoundry parent, using the existing direct BOM imports.

Update framework-boundary and release/verification documentation solely for the runtime directory
path. Update repository maintenance guidance in `AGENTS.md` and the local maintenance skill
references because their path rules are part of the project contract.

## Validation

Add a minimal Maven consumer fixture for the parent POM. Its model declares
`jfoundry-spring-boot-starter` and a Spring Boot dependency without versions, then validates the
effective inheritance through the new parent. This verifies the single-parent consumer contract
without selecting a heavyweight runtime capability.

Run Maven validation for the root reactor and the focused consumer fixture. Since this changes a
Spring Boot dependency-management entry point, run the Spring Native CI-equivalent preflight when
the required GraalVM and Docker environment is available. Also run the Java 25 release-baseline
matrix when `JAVA_25_HOME` is configured.

## Compatibility And Failure Modes

The new Parent requires the JFoundry release line's Spring Boot baseline. A consumer requiring a
different Spring Boot parent version must continue to use direct BOM imports or manage its own
parent, because Maven permits only one direct parent.

If the parent is unavailable from the configured repository, Maven fails during parent resolution
before dependency resolution. If a consumer adds a version to a managed JFoundry or Spring Boot
dependency, standard Maven nearest-definition semantics apply; the documentation will advise
against overriding managed versions unless the consumer owns compatibility verification.
