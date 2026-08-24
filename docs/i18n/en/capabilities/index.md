# Capability Catalog

Choose JFoundry by the business capability required, then select the matching runtime entry point.
The coordinates below are consumer dependencies. Domain, application, architecture, and framework-neutral
adapter modules are selected directly; runtime-specific wiring is selected through the matching runtime entry point.

Every application imports `jfoundry-dependencies` and its matching runtime BOM as described in
[Getting Started](../integration/getting-started.md). The matrix identifies the next dependency to
add; detailed guides describe required companion modules, persistence stores, transports, and
configuration.

| Capability | Use it when | Spring Boot | Quarkus | Helidon MP | Guide |
|---|---|---|---|---|---|
| Domain modeling | The business model needs aggregates, value objects, domain events, and explicit invariants. | `jfoundry-domain` | `jfoundry-domain` | `jfoundry-domain` | [Getting Started](../integration/getting-started.md) |
| Application services | Use cases need explicit application boundaries. Add CQRS, transactions, or domain-event modules only when required. | `jfoundry-application-core` | `jfoundry-application-core` | `jfoundry-application-core` | [Getting Started](../integration/getting-started.md) |
| Executable architecture rules | The project needs reusable ArchUnit checks for Hexagonal or Onion boundaries. | `jfoundry-architecture-test` (test scope) | `jfoundry-architecture-test` (test scope) | `jfoundry-architecture-test` (test scope) | [ArchUnit Architecture Rules](../framework/archunit-rules.md) |
| Application transactions | A use case needs a runtime transaction boundary. | `jfoundry-transaction-spring-boot-starter` | `jfoundry-quarkus-runtime` | `jfoundry-helidon-runtime` | [Application Transactions](application-transactions.md) |
| Aggregate persistence | An aggregate needs JPA or MyBatis-Plus persistence without turning repositories into generic query APIs. | `jfoundry-persistence-jpa-spring-boot-starter` or `jfoundry-persistence-mybatis-plus-spring-boot-starter` | `jfoundry-persistence-jpa-quarkus-runtime` | `jfoundry-persistence-jpa-helidon-runtime` | [Aggregate Persistence](aggregate-persistence.md) |
| Web | HTTP APIs need RFC 9457 problem responses, or a Spring application needs opt-in outbound `RestClient` support. | Problem Details: `jfoundry-webmvc-spring-boot-starter`; HTTP client: `jfoundry-web-spring-boot-starter` | Problem Details: `jfoundry-web-quarkus-runtime` | Problem Details: `jfoundry-web-helidon-runtime` | [Web](web.md) |
| Direct message delivery | The application needs to publish to a broker without durable Outbox recording. | `jfoundry-messaging-spring-boot-starter` plus one broker starter | `jfoundry-messaging-kafka-quarkus-runtime` or `jfoundry-messaging-rabbitmq-quarkus-runtime` | Not provided | [Message Delivery](message-delivery.md) |
| Reliable messaging | A message must be recorded transactionally, dispatched later, or processed idempotently. | `jfoundry-outbox-spring-boot-starter` or `jfoundry-inbox-spring-boot-starter` | `jfoundry-outbox-quarkus-runtime` or `jfoundry-inbox-jpa-quarkus-runtime` | `jfoundry-outbox-helidon-runtime` or `jfoundry-inbox-jpa-helidon-runtime` | [Reliable Messaging: Outbox And Inbox](reliable-messaging.md) |
| Distributed locking | A use case needs cross-instance coordination after database constraints and idempotency are insufficient. | `jfoundry-lock-redisson-spring-boot-starter` | Not provided | Not provided | [Distributed Locks](distributed-locks.md) |
| Observability | Framework operations need bounded metrics and tracing without exposing business identifiers. | `jfoundry-observability-spring-boot-starter` | `jfoundry-observability-otel` | `jfoundry-observability-otel` | [Observability](observability.md) |

`Not provided` means JFoundry does not currently publish an assembly for that runtime. Applications
can still implement their own outer adapter against the framework-neutral contracts; this is not an
implicit support claim.

## Selection Rules

- Select only the capability entry points a business use case needs. The base runtime integration
  does not implicitly add persistence, a broker, Outbox, Inbox, locking, or scheduling.
- A capability may require an explicit companion choice. For example, an Outbox runtime needs a
  selected store and a real `MessageSender`; a persistence entry point needs the application's
  datasource and migration.
- Use the runtime guides for runtime-specific configuration and replacement rules. They are not the
  primary capability-selection catalog.
- Review [Adoption Readiness and Validated Scope](../integration/adoption-readiness.md) before
  treating a runtime and capability combination as a production support claim.
