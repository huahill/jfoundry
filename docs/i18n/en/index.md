# jfoundry Documentation

This documentation is capability-first. Capability pages define contracts and behavior; persistence
and runtime pages explain technology-specific choices. Runtime-specific configuration reference is
linked from its owning runtime guide rather than presented as a technology-selection entry point.

## Getting Started

- [Getting Started](integration/getting-started.md)
- [Adoption Readiness and Validated Scope](integration/adoption-readiness.md)
- [`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills): optional AI-assisted domain modeling and architecture workflow before JFoundry landing.

## Capabilities

- [Aggregate Persistence](capabilities/aggregate-persistence.md)
- [Message Delivery](capabilities/message-delivery.md)
- [Reliable Messaging: Outbox And Inbox](capabilities/reliable-messaging.md)
- [Application Transactions](capabilities/application-transactions.md)
- [Distributed Locks](capabilities/distributed-locks.md)
- [Observability](capabilities/observability.md)

## Persistence Implementations

- [JPA](implementations/jpa.md)
- [MyBatis-Plus](implementations/mybatis-plus.md)

## Runtime Integrations

- [Spring Boot Runtime Assembly](implementations/spring-boot.md)
- [Quarkus Runtime Integration](implementations/quarkus.md)
- [Helidon MP Runtime Integration](implementations/helidon.md)

## Framework Semantics

- [Architecture Styles](framework/architecture-styles.md)
- [ArchUnit Architecture Rules](framework/archunit-rules.md)
- [Framework Boundaries](framework/framework-boundaries.md)

## Modeling

- [Value Object Guide](modeling/value-object.md)
- [Repository and Read-side Contracts](modeling/repository-vs-read-contracts.md)

## Release and Compatibility

- [Compatibility Matrix](../../release/compatibility.md)
- [Maven Central Publishing](../../release/maven-central.md)
