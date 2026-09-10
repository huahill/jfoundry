# Modeling

This section records JFoundry domain modeling conventions: how to write
aggregates, value objects, domain events, and repository contracts. It is not a
runtime capability catalog and does not select starters, brokers, or persistence
adapters.

- [Value Object Guide](value-object.md) — immutable values, equality, and ArchUnit checks.
- [Domain Events](domain-event.md) — explicit business facts recorded by an aggregate.
- [Repository and Read-side Contracts](repository-vs-read-contracts.md) — write-side
  repositories versus read-side queries.

Related runtime topics live elsewhere:

- Aggregate mapping and persistence adapters: [Aggregate Persistence](../capabilities/aggregate-persistence.md)
- Transactional Outbox and Inbox: [Reliable Messaging](../capabilities/reliable-messaging.md)
- Runtime event dispatch wiring: [Spring Boot](../implementations/spring-boot.md),
  [Quarkus](../implementations/quarkus.md), and [Helidon MP](../implementations/helidon.md)
