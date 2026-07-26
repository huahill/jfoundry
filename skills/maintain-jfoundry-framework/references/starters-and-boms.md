# Starters And BOMs

## BOM And Parent Structure

`jfoundry-boms/` is a physical source-directory grouping only. It has no aggregator POM and does not
create a published parent or inheritance boundary.

- `jfoundry-parent` is the repository's internal build parent. It imports the core
  `jfoundry-dependencies` BOM for internal modules and must not be used as a consumer-facing BOM
  parent.
- `jfoundry-foundation-dependencies` manages low-level common dependency versions.
- `jfoundry-modules-dependencies` manages JFoundry module versions.
- `jfoundry-dependencies` is the aggregate, framework-neutral public BOM. It imports only the
  foundation and module BOMs and is the required JFoundry BOM for every external application.
- `jfoundry-spring-dependencies`, `jfoundry-quarkus-dependencies`, and
  `jfoundry-helidon-dependencies` are standalone runtime BOMs. They do not import
  `jfoundry-dependencies` and manage only their own runtime platform ecosystems.

Every published BOM is an independent, self-describing POM: it must not inherit a JFoundry parent,
and it must directly declare its coordinates, project metadata (including licenses, developers, and
SCM), reproducible-build properties, and release profile. Keep the root POM's corresponding metadata
and release profile for its own publication lifecycle; Maven does not propagate them to independent
BOMs.

An external application always imports `jfoundry-dependencies`. An application using Spring Boot,
Quarkus, or Helidon additionally imports exactly the matching runtime BOM. Do not make a runtime BOM
implicitly carry JFoundry module versions, and do not use `jfoundry-parent` outside this repository.

Runtime BOM overrides must be exceptional, platform-local, and documented with the upstream reason and
validation scope. The Helidon `groovy-all` compatibility override is an example: it exists solely for
Maven release dependency validation and is not a general dependency-management pattern.

## Runtime Platform Ecosystem Scope

A runtime BOM is a version-management contract for the supported runtime platform ecosystem, not only
for dependencies used directly by JFoundry runtime adapters. It may import the runtime's official
platform BOM and official Cloud or integration BOMs that business applications commonly compose with
that runtime. It never adds those libraries to an application's runtime classpath by itself; the
application still declares each selected starter or client explicitly.

For example, `jfoundry-spring-dependencies` manages the aligned Spring Boot, Spring Cloud, and Spring
Cloud Alibaba BOMs. This allows a Spring application to add an appropriate Cloud starter without a
version while keeping the choice of configuration server, service discovery, traffic management, or
other platform capability explicit in the application.

Do not add every available ecosystem BOM to a runtime BOM. Add one only when all of the following hold:

1. It is an official, maintained platform or integration BOM.
2. Its supported version line is compatible with the runtime baseline managed by JFoundry.
3. The dependency-management composition is validated by a versionless consumer dependency-resolution
   check.
4. The compatibility matrix and user-facing runtime documentation record the managed version line and
   its scope.

Managing an ecosystem BOM does not mean JFoundry provides an adapter for every library in that
ecosystem. A JFoundry adapter remains a separate module, API, and runtime-verification decision.

When adding a module or third-party dependency, update the narrowest relevant BOM and any aggregate BOM that imports it.

## Starter Rules

Starters are user-facing dependency entry points. They should:

- contain POM dependencies only;
- avoid Java runtime logic;
- remain capability-specific;
- avoid surprising transitive dependencies;
- make heavy capabilities explicit.

## Core Starter Layout

`jfoundry-core/jfoundry-starters` is a runtime-neutral dependency-composition group, not an Onion
ring implementation. Keep Domain and Application starters directly under that directory. Place
runtime-neutral starters that compose infrastructure adapters directly under
`jfoundry-core/jfoundry-starters/infrastructure`.

- Use capability and technology artifact IDs, such as `jfoundry-persistence-jpa-starter` and
  `jfoundry-persistence-mybatis-plus-starter`; do not expose the `infrastructure` directory category
  in the artifact ID.
- Do not add an `infrastructure/pom.xml` or further nested starter directories.
- Keep runtime-managed clients, broker senders, and platform lifecycle assembly in the matching
  Spring, Quarkus, or Helidon integration rather than adding them to the core starter catalog.

Default Spring Boot starter:

- keep `jfoundry-spring-boot-starter` minimal;
- do not implicitly include MyBatis-Plus stores, Outbox, Inbox, broker adapters, JobRunr, or middleware clients.

Capability starters:

- `jfoundry-event-spring-boot-starter`: local domain event publication.
- `jfoundry-messaging-spring-boot-starter`: messaging contracts, Spring Boot JSON support, Jackson
  serializer; it must support non-web applications without requiring a
  WebMVC or WebFlux starter.
- `jfoundry-messaging-<broker>-spring-boot-starter`: concrete broker sender adapter.
- `jfoundry-messaging-kafka-spring-boot-starter`: Kafka sender adapter.
- `jfoundry-messaging-rabbitmq-spring-boot-starter`: RabbitMQ sender adapter.
- `jfoundry-messaging-rocketmq-spring-boot-starter`: RocketMQ sender adapter.
- `jfoundry-outbox-spring-boot-starter`: Outbox core with Spring transaction/scheduling integration.
- `jfoundry-outbox-mybatis-plus-spring-boot-starter`: MyBatis-Plus Outbox store.
- `jfoundry-outbox-jobrunr-spring-boot-starter`: JobRunr dispatcher.
- `jfoundry-inbox-spring-boot-starter`: Inbox core and `InboxTemplate`.
- `jfoundry-inbox-mybatis-plus-spring-boot-starter`: MyBatis-Plus Inbox store.
- `jfoundry-persistence-mybatis-plus-spring-boot-starter`: Spring Boot runtime assembly for business MyBatis-Plus persistence, not Outbox/Inbox stores.
- `jfoundry-persistence-jpa-spring-boot-starter`: explicit Spring Boot runtime assembly for business Jakarta Persistence adapters.
- `jfoundry-webmvc-spring-boot-starter`: Web MVC ProblemDetail support.

## Before Changing A Starter

Check:

1. Is this dependency needed by every user of the starter?
2. Does it make an optional capability implicit?
3. Does it pull a broker, ORM, migration tool, or scheduler into the default path?
4. Does auto-configuration still have matching conditions?
5. Does README or `../../../docs/i18n/en/integration/getting-started.md` need an update? If the change affects localized user guidance, update the matching `../../../docs/i18n/zh/` page too.
6. Does the compatibility matrix need a version entry?

## Release Compatibility

For the first release line:

- Java compile target: 25
- Runtime Java baseline: 25
- Spring Boot: 3.5.x
- Spring Framework: 6.2.x
- Maven release tool: 3.9.x

Do not silently move the first release line to Spring Boot 4, Spring Framework 7, or Jakarta EE 11. Treat that as a separate compatibility line unless the project explicitly changes release policy.
