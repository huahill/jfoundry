# Starters And BOMs

## BOM And Parent Structure

`jfoundry-boms/` is a physical source-directory grouping only. It has no aggregator POM and does not
create a published parent or inheritance boundary.

- `jfoundry-parent` is the repository's internal build parent. It imports the core
  `jfoundry-dependencies` BOM for internal modules and must not be used as a consumer-facing BOM
  parent.
- `jfoundry-spring-boot-parent` is the consumer-facing Boot-only parent. It directly inherits
  the supported Spring Boot parent declared in its POM and imports `jfoundry-spring-boot-dependencies` before
  `jfoundry-dependencies`.
- Cloud applications use their own or a standard Maven parent compatible with the supported Cloud
  line and explicitly import `jfoundry-spring-cloud-dependencies`
  before `jfoundry-dependencies`.
- `jfoundry-foundation-dependencies` manages low-level, runtime-neutral dependency versions and
  coordinates. It does not manage runtime starters, deployment artifacts, or runtime-specific Native
  Image integrations.
- `jfoundry-modules-dependencies` manages JFoundry module versions.
- `jfoundry-dependencies` is the aggregate, framework-neutral public BOM. It imports only the
  foundation and module BOMs and is the required JFoundry BOM for every external application.
- `jfoundry-spring-boot-dependencies`, `jfoundry-spring-cloud-dependencies`,
  `jfoundry-quarkus-dependencies`, and `jfoundry-helidon-dependencies` are standalone runtime BOMs.
  They do not import `jfoundry-dependencies` or `jfoundry-foundation-dependencies`; they manage only
  their runtime platform ecosystem. The aggregate `jfoundry-dependencies` BOM is the single public
  entry point for runtime-neutral, Foundation-managed coordinates.

Every published BOM is an independent, self-describing POM: it must not inherit a JFoundry parent,
and it must directly declare its coordinates, project metadata (including licenses, developers, and
SCM), reproducible-build properties, and release profile. Keep the root POM's corresponding metadata
and release profile for its own publication lifecycle; Maven does not propagate them to independent
BOMs.

An external application using either Spring parent does not import JFoundry BOMs directly. An
application using another parent imports exactly one matching runtime BOM before
`jfoundry-dependencies`; Maven applies the first imported management entry, so this preserves the
runtime platform's tested constraints while Foundation supplies components the platform does not manage.
Do not make a runtime BOM implicitly carry JFoundry module versions, and do not use `jfoundry-parent`
outside this repository. The Boot-only and Cloud Spring runtime BOMs are mutually exclusive. The
former combined Spring runtime coordinate is intentionally removed without a compatibility alias.

Runtime BOM overrides must be exceptional, platform-local, and documented with the upstream reason and
validation scope. The Helidon `groovy-all` release-validation override and its Jackson annotations
alignment with Foundation's Jackson 3 line are examples; neither is a general dependency-management
pattern.

## Runtime Platform Ecosystem Scope

A runtime BOM is a version-management contract for the supported runtime platform ecosystem, not only
for dependencies used directly by JFoundry runtime adapters. It may import the runtime's official
platform BOM and official Cloud or integration BOMs that business applications commonly compose with
that runtime. It never adds those libraries to an application's runtime classpath by itself; the
application still declares each selected starter or client explicitly.

`jfoundry-spring-boot-dependencies` manages only the supported Boot-only Spring Boot line. The separate
`jfoundry-spring-cloud-dependencies` line manages the supported Spring Cloud and Spring Cloud Alibaba
line; the Cloud application's parent or another explicit BOM manages Spring Boot. Exact platform
baselines belong in `docs/release/compatibility.md`. This
allows a Cloud application to add an appropriate Cloud starter without a version while keeping the
choice of configuration server, service discovery, traffic management, or other platform capability
explicit in the application.

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

For a third-party component family used by runtime adapters, split ownership by coordinate rather than
placing the entire family in Foundation. Foundation manages only the family's runtime-neutral artifacts.
The matching runtime BOM manages runtime starters, deployment artifacts, and runtime-specific Native
Image integrations, using the same compatible component version when appropriate. JobRunr,
MyBatis-Plus, Redisson, and jMolecules Integrations follow this rule: their neutral artifacts remain in
Foundation while their Spring-specific artifacts belong in `jfoundry-spring-boot-dependencies`.
Do not separately override a starter's transitive dependencies unless JFoundry has an explicit,
documented compatibility reason and verifies the replacement combination. In particular,
`org.mybatis:mybatis-spring` follows `mybatis-plus-spring-boot4-starter` and is not managed by a
JFoundry BOM.

## Starter Rules

Starters are user-facing dependency entry points. They should:

- contain POM dependencies only;
- avoid Java runtime logic;
- remain capability-specific;
- avoid surprising transitive dependencies;
- make heavy capabilities explicit.

## Runtime-Neutral Dependency Entry Points

Do not publish runtime-neutral starter wrappers. Consumers depend directly on the Domain, Application,
architecture-style, and framework-neutral adapter modules they use. In particular, architecture style is
an explicit project choice: select `jfoundry-hexagonal` or `jfoundry-onion` instead of allowing a runtime
integration to select or combine them implicitly.

Keep runtime-managed clients, broker senders, and platform lifecycle assembly in the matching Spring,
Quarkus, or Helidon integration.

Default Spring Boot starter:

- keep `jfoundry-spring-boot-starter` minimal;
- do not implicitly include MyBatis-Plus stores, Outbox, Inbox, broker adapters, JobRunr, or middleware clients.

Capability starters:

- `jfoundry-domain-event-spring-boot-starter`: local domain event publication.
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

For the current release line:

- Java compile target: 25
- Runtime Java baseline: 25
- Exact runtime platform and release-tool baselines: `docs/release/compatibility.md`

Do not silently move a runtime or Jakarta EE major line. Treat that as a separate compatibility line
unless the project explicitly changes release policy.
