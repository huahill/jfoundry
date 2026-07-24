# Starters And BOMs

## BOM Layers

- `jfoundry-dependencies`: aggregate framework-neutral BOM exposed to users.
- `jfoundry-modules-dependencies`: jfoundry module versions.
- `jfoundry-foundation-dependencies`: low-level common dependencies.
- `jfoundry-spring-official-dependencies`: Spring official ecosystem versions.
- `jfoundry-spring-integration-dependencies`: Spring integration dependencies and optional technology versions.
- `jfoundry-spring-dependencies`: aggregate Spring business application BOM.

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

For stable 1.x:

- Java compile target: 21
- Runtime Java baseline: 21
- Spring Boot: 3.5.x
- Spring Framework: 6.2.x
- Maven release tool: 3.9.x

Do not silently move 1.x to Spring Boot 4, Spring Framework 7, Java 25 compile target, or Jakarta EE 11. Treat that as a 2.x compatibility line unless the project explicitly changes release policy.
