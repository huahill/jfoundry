# Framework Boundaries

This document is for maintainers and contributors. It defines where framework code belongs and how
jfoundry keeps its core independent of runtime frameworks.

## Core Decision

jfoundry core modules must not depend on application runtimes such as Spring, Spring Boot, Helidon,
Quarkus, Micronaut, CDI, or Jakarta EE runtime integration APIs. Stable low-intrusion libraries
such as jMolecules and `slf4j-api` may appear in core modules when they express contracts.

`jfoundry-core` is a directory group for runtime-neutral framework modules. It contains the domain,
architecture, application, infrastructure, and runtime-neutral starter aggregates; it does not change
the Onion dependency direction within those modules. `jfoundry-runtime-integrations` groups concrete
runtime integrations: Spring uses `runtime/`, `autoconfigure/`, and `starters/`, Quarkus uses `runtime/`,
`deployment/`, and `integration-tests/`, and Helidon uses `runtime/` and `integration-tests/`.

## Module Roles

| Area | Modules |
|------|---------|
| Domain and architecture | `jfoundry-domain`, `jfoundry-architecture`, `jfoundry-hexagonal`, `jfoundry-onion`, `jfoundry-cqrs` |
| Application contracts | `jfoundry-application-core`, `jfoundry-transaction-core`, `jfoundry-event-core`, `jfoundry-event-externalization-core`, `jfoundry-messaging-core`, `jfoundry-outbox-core`, `jfoundry-inbox-core` |
| Framework-neutral adapters | `jfoundry-persistence-core`, `jfoundry-persistence-mybatis-plus`, `jfoundry-persistence-jpa`, `jfoundry-messaging-jackson`, Outbox/Inbox MyBatis-Plus and JPA stores, JobRunr dispatch adapter |
| Runtime-neutral starter composition | `jfoundry-core/jfoundry-starters` for Domain and Application starters; `jfoundry-core/jfoundry-starters/infrastructure` for capability-named infrastructure adapter starters |
| Spring runtime integration | `jfoundry-runtime-integrations/jfoundry-spring/runtime/*` |
| Spring Boot integration | `jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure`, `jfoundry-runtime-integrations/jfoundry-spring/starters/*` |
| Quarkus runtime integration | `jfoundry-runtime-integrations/jfoundry-quarkus/runtime/*`, `deployment/*` |
| Helidon MP runtime integration | `jfoundry-runtime-integrations/jfoundry-helidon/runtime/*`, `integration-tests/*` |
| Verification | `jfoundry-verification/*` |

## Placement Rules

- Spring Framework lifecycle, transaction synchronization, scheduling, event publishing, MVC APIs,
  and Spring-side client wrappers belong under `../../../../jfoundry-runtime-integrations/jfoundry-spring/runtime`.
- Spring Boot conditions, `@ConfigurationProperties`, bean wiring, metadata, and
  `AutoConfiguration.imports` belong under `../../../../jfoundry-runtime-integrations/jfoundry-spring/autoconfigure/jfoundry-spring-boot-autoconfigure`.
- Helidon CDI lifecycle, JTA, JAX-RS, scheduling, and JPA integration belong under
  `jfoundry-runtime-integrations/jfoundry-helidon/runtime`; consumer verification belongs under its
  `integration-tests` directory. Helidon has no JFoundry deployment module or starter layer.
- Starters are dependency entry points only; they must not contain runtime behavior. Keep Domain and
  Application starters at `jfoundry-core/jfoundry-starters`; place runtime-neutral infrastructure
  adapter starters directly under `jfoundry-core/jfoundry-starters/infrastructure`. Their artifact IDs
  use capability and technology names, such as `jfoundry-persistence-jpa-starter`; do not add an
  intermediate aggregator POM or further directory levels.
- Framework-neutral database, serializer, and scheduler adapters belong under
  `jfoundry-core/jfoundry-infrastructure`.
- Broker client `MessageSender` adapters belong to their runtime integration. The application-layer
  `MessageSender` and `SendResult` contracts remain runtime-neutral.
- Middleware integration tests and Testcontainers compatibility checks belong under
  `jfoundry-verification`.

## Reliable Messaging Boundary

`jfoundry-outbox-core` owns the message model, store contract, dispatch service, retry/backoff
contract, and state machine.

`jfoundry-outbox-spring` owns Spring runtime integration such as transaction synchronization,
scheduled dispatching, and domain-event recording in a Spring runtime.

`jfoundry-spring-boot-autoconfigure` owns Outbox configuration properties, conditions, and bean
wiring. `OutboxDispatcherProperties` and related properties live there because property binding is
a Boot concern.

`jfoundry-outbox-jobrunr` is a pure JobRunr dispatch adapter. Its Spring Boot auto-configuration
also belongs under `jfoundry-spring-boot-autoconfigure`.

`jfoundry-outbox-jpa` and `jfoundry-inbox-jpa` are framework-neutral Jakarta Persistence adapters.
They implement the Outbox and Inbox store SPIs without requiring Spring or Spring Boot. Their
Spring Boot starters, `jfoundry-outbox-jpa-spring-boot-starter` and
`jfoundry-inbox-jpa-spring-boot-starter`, are explicit capability choices; the general
`jfoundry-persistence-jpa-spring-boot-starter` provides business JPA runtime assembly only and adds neither
store.

Implementation mechanics and database limitations belong in the [JPA implementation guide](../implementations/jpa.md).
The capability state model and SQL-template policy belong in [Reliable Messaging](../capabilities/reliable-messaging.md).

## Acceptance Criteria

- Core modules have no compile/provided dependency on Spring, Spring Boot, Helidon, Quarkus,
  Micronaut, CDI, Jakarta runtime APIs, broker clients, or persistence framework details.
- Adapter modules do not register Spring Boot auto-configuration directly.
- Starters remain lightweight dependency choices.
- Future runtime integrations can reuse core SPI and framework-neutral adapters without depending
  on Spring Boot.
