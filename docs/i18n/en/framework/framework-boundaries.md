# Framework Boundaries

This document is for maintainers and contributors. It defines where framework code belongs and how
jfoundry keeps its core independent of runtime frameworks.

## Core Decision

jfoundry core modules must not depend on application runtimes such as Spring, Spring Boot, Helidon,
Quarkus, Micronaut, CDI, or Jakarta EE runtime integration APIs. Stable low-intrusion libraries
such as jMolecules and `slf4j-api` may appear in core modules when they express contracts.

`jfoundry-core` is a directory group for runtime-neutral framework modules. It contains the domain,
architecture, application, and infrastructure modules; it does not change
the Onion dependency direction within those modules. `jfoundry-runtime` groups outer runtime adapters.
`jfoundry-jakarta` contains portable JAX-RS and JTA implementations shared by Jakarta-based runtimes;
it does not own CDI registration or a container lifecycle. Spring uses `runtime/`, `autoconfigure/`, and
`starters/`; Quarkus uses `runtime/` and `deployment/`; each runtime also has one direct
`jfoundry-<runtime>-integration-tests` module.

## Module Roles

| Area | Modules |
|------|---------|
| Domain and architecture | `jfoundry-domain`, `jfoundry-architecture`, `jfoundry-hexagonal`, `jfoundry-onion`, `jfoundry-cqrs` |
| Application contracts | `jfoundry-application-core`, `jfoundry-transaction-core`, `jfoundry-domain-event-core`, `jfoundry-domain-event-externalization-core`, `jfoundry-messaging-core`, `jfoundry-outbox-core`, `jfoundry-inbox-core` |
| Framework-neutral adapters | `jfoundry-persistence-core`, `jfoundry-persistence-jpa`, `jfoundry-persistence-mybatis-plus`, `jfoundry-messaging-jackson`, Outbox/Inbox JPA and MyBatis-Plus stores, JobRunr dispatch adapter |
| Shared Jakarta adapters | `jfoundry-web-jaxrs`, `jfoundry-transaction-jta`, `jfoundry-domain-event-jta` |
| Spring runtime integration | `jfoundry-runtime/jfoundry-spring/runtime/*` |
| Spring Boot integration | `jfoundry-runtime/jfoundry-spring/autoconfigure/*`, `jfoundry-runtime/jfoundry-spring/starters/*` |
| Spring integration tests | `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests` |
| Quarkus runtime integration | `jfoundry-runtime/jfoundry-quarkus/runtime/*`, `deployment/*` |
| Quarkus integration tests | `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests` |
| Helidon MP runtime integration | `jfoundry-runtime/jfoundry-helidon/runtime/*` |
| Helidon integration tests | `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests` |

## Placement Rules

- Spring Framework lifecycle, transaction synchronization, scheduling, event publishing, MVC APIs,
  and Spring-side client wrappers belong under `../../../../jfoundry-runtime/jfoundry-spring/runtime`.
- Spring Boot conditions, `@ConfigurationProperties`, bean wiring, metadata, and
  `AutoConfiguration.imports` belong in their capability-specific module under `../../../../jfoundry-runtime/jfoundry-spring/autoconfigure`.
- Spring middleware and Testcontainers verification belongs in
  `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests`.
- Portable JAX-RS filtering and body logging, Jakarta Transactions execution, and JTA domain-event
  coordination shared by multiple runtimes belong under `jfoundry-runtime/jfoundry-jakarta`. These modules
  do not register CDI beans or providers; concrete runtimes remain responsible for discovery, lifecycle,
  configuration keys, logging bridges, build-time processing, and Native Image integration.
- Quarkus runtime and Native Image verification belongs in
  `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests`. Future Quarkus
  middleware or Testcontainers verification belongs in the same module.
- Helidon CDI lifecycle, JTA, JAX-RS, scheduling, and JPA integration belong under
  `jfoundry-runtime/jfoundry-helidon/runtime`; current Native Image verification belongs in
  `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests`. Future Helidon middleware
  or Testcontainers verification belongs in the same module. Helidon has no
  JFoundry deployment module or starter layer.
- Consume Domain, Application, architecture-style, and framework-neutral adapter modules directly.
  Runtime-specific starters are dependency entry points only and must not contain runtime behavior.
- Framework-neutral database, serializer, and scheduler adapters belong under
  `jfoundry-core/jfoundry-infrastructure`.
- Broker client `MessageSender` adapters belong to their runtime integration. The application-layer
  `MessageSender` and `SendResult` contracts remain runtime-neutral.
- Runtime-specific middleware integration tests and Testcontainers compatibility checks belong in the affected
  runtime's direct integration-test module. Framework-neutral tests stay next to their core or infrastructure
  implementation.

## Dependency Management Boundaries

`jfoundry-foundation-dependencies` owns only runtime-neutral libraries and test utilities. A component
family may have neutral coordinates in Foundation while its runtime-specific starters, deployment
artifacts, or Native Image integrations remain outside it. For example, Foundation manages the neutral
MyBatis-Plus, JobRunr, Redisson, and jMolecules coordinates, but it does not manage their Spring-specific
artifacts.

Each runtime BOM owns its own ecosystem: `jfoundry-spring-boot-dependencies` owns Spring Boot and
Spring-specific integration coordinates, `jfoundry-quarkus-dependencies` owns Quarkus coordinates, and
`jfoundry-helidon-dependencies` owns Helidon coordinates. Runtime BOMs remain independent and must not
import Foundation or another runtime BOM. A runtime BOM may carry a narrow, documented compatibility
override when its official platform BOM would otherwise break a Foundation-managed neutral component;
the Helidon Jackson annotations alignment is one such exception.

Test dependencies follow the same boundary. Core modules may use runtime-neutral JUnit, AssertJ, Mockito,
H2, or native persistence-framework test support. Tests that bootstrap Spring, Quarkus, or Helidon belong
in the matching direct runtime integration-test module and declare that runtime's test stack there.

Jakarta specifications are not application runtimes by themselves. A framework-neutral infrastructure
adapter may depend narrowly on a portable specification API when that API expresses the adapter's technical
contract; `jfoundry-web` using the optional Jakarta Validation API to convert `ConstraintViolation` values is
one example. This allowance does not apply to Domain or Application modules. Portable container-facing
implementations shared by more than one runtime belong in `jfoundry-jakarta`, not Core; CDI lifecycle,
runtime registration, providers, and runtime exception classification remain in the concrete runtime adapters.

CI runs `scripts/verify-dependency-boundaries.sh` before Maven tests. The XML-aware checker scans every
reactor POM, including test dependencies and dependency management, and rejects cross-runtime coordinates,
runtime dependencies in Core, and runtime-specific coordinates in Foundation. Its fixture suite and the
workflow self-check make removal or weakening of this gate visible in CI.

## Java Nullness Contracts

Domain and Application packages use JSpecify `@NullMarked` to make reference types non-null by default.
Public API positions that legitimately accept or return `null` use `@Nullable`; examples include a missing
aggregate from `AggregateRepository.findById`, optional message routing keys, and optional Outbox/Inbox
state. The mutable `InboxMessage` and `OutboxMessage` persistence carriers remain `@NullUnmarked` at the
class boundary because their no-argument construction and mapper hydration create a temporarily incomplete
object. Their stable optional properties are still annotated explicitly.

These annotations are Java static-analysis metadata. They do not perform runtime validation and do not
replace constructor checks, `Objects.requireNonNull`, domain invariants, or Jakarta Validation at an HTTP or
container boundary. New Domain and Application packages should be `@NullMarked`; use `@Nullable` only when
`null` is part of the supported contract, and use `@NullUnmarked` only as a narrow migration boundary for a
lifecycle that cannot yet express a sound static contract.

## Reliable Messaging Boundary

`jfoundry-outbox-core` owns the message model, store contract, dispatch service, retry/backoff
contract, and state machine.

`jfoundry-outbox-spring` owns Spring runtime integration such as transaction synchronization,
scheduled dispatching, and domain-event recording in a Spring runtime.

`jfoundry-outbox-spring-boot-autoconfigure` owns Outbox configuration properties, conditions, and bean
wiring. `OutboxDispatcherProperties` and related properties live there because property binding is
a Boot concern.

`jfoundry-outbox-jobrunr` is a pure JobRunr dispatch adapter. Its Spring Boot auto-configuration
also belongs under `jfoundry-outbox-spring-boot-autoconfigure`.

`jfoundry-outbox-jpa` and `jfoundry-inbox-jpa` are framework-neutral Jakarta Persistence adapters.
They implement the Outbox and Inbox store SPIs without requiring Spring or Spring Boot. Their
Spring Boot starters, `jfoundry-outbox-jpa-spring-boot-starter` and
`jfoundry-inbox-jpa-spring-boot-starter`, are explicit capability choices; the general
`jfoundry-persistence-jpa-spring-boot-starter` provides business JPA runtime assembly only and adds neither
store.

Implementation mechanics and database limitations belong in the [JPA implementation guide](../implementations/jpa.md).
The capability state model and SQL-template policy belong in [Reliable Messaging](../capabilities/reliable-messaging.md).

## Merge Verification

All changes enter `main` through a pull request and GitHub's `Rebase and merge` strategy; direct pushes are
not permitted. The always-running `Merge gate` is the required status check. It accepts a documentation-only
change only when documentation verification succeeds. For any code change, it requires every existing CI job,
including runtime middleware and Native Image verification, to succeed. A skipped, cancelled, or failed runtime
job does not satisfy the gate.

Contributors should run the CI-equivalent stage for the capability they change before pushing a branch. Local
verification reduces feedback time but cannot replace the server-side gate.

## Acceptance Criteria

- Domain and Application modules have no compile/provided dependency on Spring, Spring Boot, Helidon,
  Quarkus, Micronaut, CDI, Jakarta APIs, broker clients, or persistence framework details. Infrastructure
  adapters may use a narrowly scoped portable Jakarta specification API but not container integration APIs.
- Adapter modules do not register Spring Boot auto-configuration directly.
- Starters remain lightweight dependency choices.
- Future runtime integrations can reuse core SPI and framework-neutral adapters without depending
  on Spring Boot.
- Jakarta-based runtimes reuse `jfoundry-jakarta` implementations while retaining runtime-local registration,
  lifecycle, configuration, logging, build-time, and Native Image behavior.
- Foundation manages only runtime-neutral coordinates; each runtime BOM owns its matching ecosystem.
- Core tests do not obtain runtime test frameworks through broad starter dependencies.
- Domain and Application packages declare Java nullness defaults with JSpecify and explicitly annotate
  supported nullable API positions.
