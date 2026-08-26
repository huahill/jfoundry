# Feature Placement

Use this file before adding modules, classes, annotations, rules, adapters, starters, or docs.

## Placement Map

| Change | Location |
|---|---|
| Domain building block, entity/value object/event abstraction | `jfoundry-domain` |
| Architecture facade annotation | `jfoundry-core/jfoundry-architecture/jfoundry-hexagonal` or `jfoundry-core/jfoundry-architecture/jfoundry-onion` |
| CQRS annotation or dispatcher contract | `jfoundry-core/jfoundry-architecture/jfoundry-cqrs` |
| Reusable ArchUnit rule or test helper | `jfoundry-core/jfoundry-architecture/jfoundry-architecture-test` |
| Application service marker or application-layer contract | `jfoundry-core/jfoundry-application/jfoundry-application-core` |
| Application transaction abstraction or `TransactionRunner` contract | `jfoundry-core/jfoundry-application/jfoundry-transaction-core` |
| Domain event dispatch contract | `jfoundry-core/jfoundry-application/jfoundry-domain-event-core` |
| Domain event externalization metadata or routing rules | `jfoundry-core/jfoundry-application/jfoundry-domain-event-externalization-core` |
| Message sending or payload serialization SPI | `jfoundry-core/jfoundry-application/jfoundry-messaging-core` |
| Outbox state, store contract, dispatcher service, retry/backoff core | `jfoundry-core/jfoundry-application/jfoundry-outbox-core` |
| Inbox state, store contract, `InboxTemplate` | `jfoundry-core/jfoundry-application/jfoundry-inbox-core` |
| Runtime-neutral HTTP problem semantics and exception-to-response policy | `jfoundry-core/jfoundry-infrastructure/jfoundry-web` |
| MyBatis-Plus business persistence adapter | `jfoundry-core/jfoundry-infrastructure/jfoundry-persistence-mybatis-plus` |
| Jakarta Persistence business persistence adapter | `jfoundry-core/jfoundry-infrastructure/jfoundry-persistence-jpa` |
| MyBatis-Plus Outbox/Inbox store adapter | `jfoundry-core/jfoundry-infrastructure/jfoundry-outbox-mybatis-plus` or `jfoundry-core/jfoundry-infrastructure/jfoundry-inbox-mybatis-plus` |
| Broker `MessageSender` adapter | Matching runtime integration module, such as `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-messaging-spring` or `jfoundry-runtime/jfoundry-quarkus/runtime/jfoundry-messaging-<broker>-quarkus-runtime` |
| Payload serializer adapter | `jfoundry-core/jfoundry-infrastructure/jfoundry-messaging-jackson` |
| Pure JobRunr dispatcher adapter | `jfoundry-core/jfoundry-infrastructure/jfoundry-outbox-jobrunr` |
| Spring Framework local domain-event adapter | `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-domain-event-spring` |
| Spring transaction adapter | `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-transaction-spring` |
| Spring messaging transport adapter | `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-messaging-spring` |
| Spring Outbox transaction/scheduling adapter | `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-outbox-spring` |
| Spring Web MVC ProblemDetail adapter | `jfoundry-runtime/jfoundry-spring/runtime/jfoundry-webmvc-spring` |
| Spring Boot conditions/properties/wiring | A capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure` |
| Spring runtime or middleware integration verification | `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests` |
| Portable JAX-RS or JTA implementation shared by Jakarta runtimes | `jfoundry-runtime/jfoundry-jakarta` |
| Quarkus runtime extension behavior | `jfoundry-runtime/jfoundry-quarkus/runtime` |
| Quarkus build-time processor or Native Image registration | `jfoundry-runtime/jfoundry-quarkus/deployment` |
| Quarkus consumer, middleware, or Native Image integration verification | `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests` |
| Helidon MP CDI, JTA, JAX-RS, scheduling, or JPA runtime behavior | `jfoundry-runtime/jfoundry-helidon/runtime` |
| Helidon MP consumer, middleware, or Native Image integration verification | `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests` |
| User dependency entry point | A direct Domain, Application, architecture-style, or framework-neutral adapter dependency; use `jfoundry-runtime/jfoundry-spring/starters` for Spring Boot assembly |
| Framework-neutral unit or adapter verification | Next to the core or infrastructure implementation under test |

## Decision Rules

- If the code defines an abstraction used by multiple runtimes, keep it framework-neutral.
- Runtime-neutral does not imply application-layer ownership. Transport representations, protocol status mappings,
  and exception-to-response policies remain inbound adapters; place shared implementations in infrastructure or a
  dedicated adapter module.
- Keep persistence-context state and awareness contracts in `jfoundry-persistence-core`; place
  transaction-scoped implementations in runtime adapters and bean-lifecycle injection in Spring
  Boot auto-configuration. Business repository constructors should not expose runtime context.
- If the code uses Spring transaction synchronization, `ApplicationEventPublisher`, scheduling, MVC APIs, or bean lifecycle, put it under `jfoundry-runtime/jfoundry-spring/runtime`.
- If the code registers Spring Boot beans conditionally or binds `@ConfigurationProperties`, put it in the matching capability-specific module under `jfoundry-runtime/jfoundry-spring/autoconfigure`.
- If a test verifies middleware behavior through Spring's runtime wiring or Testcontainers, put it under `jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests`.
- If the code uses Quarkus build steps, augmentation APIs, or Native Image build items, put it under `jfoundry-runtime/jfoundry-quarkus/deployment`; otherwise put Quarkus CDI runtime behavior under `jfoundry-runtime/jfoundry-quarkus/runtime`.
- If JAX-RS or JTA implementation code is semantically identical across Jakarta-based runtimes, put the
  portable implementation under `jfoundry-runtime/jfoundry-jakarta`; keep CDI registration, provider discovery,
  configuration ownership, runtime logging bridges, build-time processing, and Native Image integration in the
  concrete runtime modules.
- If a test verifies Quarkus runtime wiring, middleware, Testcontainers, or Native Image behavior, put it under `jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests`.
- If the code uses Helidon MP CDI lifecycle, Jakarta transactions, JAX-RS, scheduling, or Helidon JPA integration, put it under `jfoundry-runtime/jfoundry-helidon/runtime`. Keep Helidon Native consumer checks under `jfoundry-helidon/jfoundry-helidon-integration-tests`; do not create a Quarkus-style deployment module without an upstream Helidon build-time extension model.
- If a test verifies Helidon runtime wiring, middleware, Testcontainers, or Native Image behavior, put it under `jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests`.
- If an auto-configuration condition depends on a bean created by another auto-configuration, declare the ordering explicitly and test the real upstream auto-configuration chain instead of only pre-registering the bean in a context runner.
- If the code only selects dependencies for users, put it in a starter POM.
- If the code talks to a concrete database, ORM, serializer, or scheduler but does not require Spring Boot wiring, put it in `jfoundry-infrastructure`.
- If the code implements `MessageSender` through a concrete broker client, place it in the runtime integration that manages that client. Keep only the `MessageSender` and `SendResult` contracts runtime-neutral.
- Keep framework-neutral tests next to the core or infrastructure implementation they verify; use the direct runtime-local integration-test module only when the behavior depends on a concrete runtime.

## Public API Discipline

Public API Javadoc must be English. Keep Javadoc concise and focused on intent.

When changing public types:

- preserve binary/source compatibility when practical;
- document behavioral changes;
- add tests for new contracts;
- consider whether starter dependencies or docs need updates;
- call out migration impact in the final response.

## Documentation Placement

- Business-facing feature docs go under the matching language path in `../../../docs/i18n/en/` and `../../../docs/i18n/zh/`.
- Release and compatibility docs are maintainer/project-operation documents and go under `docs/release/`; keep one authoritative copy unless the project explicitly decides to localize release operations.
- Framework maintainer rules may be summarized in this skill and should reference the docs rather than duplicate long explanations.
- Do not create scattered ad hoc notes when an existing doc has the same topic.
