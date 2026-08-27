# JPA Implementation

This implementation satisfies the [aggregate persistence](../capabilities/aggregate-persistence.md)
and [reliable messaging](../capabilities/reliable-messaging.md) contracts using Jakarta Persistence.

## Aggregate Persistence

The supported default is one JPA-managed entity graph for one aggregate. `JpaAggregateRepository`
loads that graph with `EntityManager.find`, tracks the managed root, applies aggregate changes to
the same graph, and flushes before reporting success. It never calls `merge`; load, domain behavior,
and `modify(...)` must remain in one transaction and persistence context.

`JpaAggregateMapper` creates and restores the entity graph, converts IDs, and synchronizes current
aggregate state to the managed graph. It does not provide generic manual synchronization across
multiple tables or entity graphs.

For optimistic concurrency, put `@Version` on the entity-graph root and ensure every graph mutation
changes a persistent root attribute. Child-only changes do not participate merely because the root
has `@Version`; the mapper may explicitly touch the root while applying a child change. A concurrent
root update is reported as `ConflictException` at repository flush.

Use `jfoundry-persistence-jpa-spring-boot-starter` for JPA business runtime assembly. It does not add Outbox or
Inbox stores.

For Quarkus, add `jfoundry-transaction-quarkus-runtime`, `jfoundry-persistence-quarkus-runtime`,
`jfoundry-persistence-jpa`,
`jfoundry-persistence-jpa-quarkus-runtime`, Quarkus Hibernate ORM, and the selected datasource
extension. A CDI-managed `JpaAggregateRepository` receives its
transaction-scoped `AggregatePersistenceContext` from the jfoundry Quarkus extension. Use
`TransactionRunner` as the transaction boundary; do not create or set the context in business code.
The JPA Quarkus capability translates known Hibernate availability failures and can be replaced with
an application CDI `PersistenceFailureTranslator`.
See [Quarkus](quarkus.md) for the runtime assembly requirements.

For Helidon MP, add `jfoundry-persistence-jpa-helidon`, then configure the application's
Helidon CDI JPA/Hibernate integration, datasource, and persistence unit. This artifact brings in
`jfoundry-persistence-jpa` and `jfoundry-persistence-helidon`; the latter supplies the
transaction-bound persistence context, while the Helidon JPA artifact adds the recognized Hibernate
availability-failure translation. See [Helidon MP](helidon.md) for its explicit dependency composition and Native Image
limitations.

### Direct JPA Or Hibernate Assembly

The JPA adapter is runtime-neutral, not a turnkey raw-Hibernate bootstrap. Outside Spring Boot and
the supported Quarkus and Helidon assembly, the application creates the `EntityManagerFactory`, starts and
completes each transaction, registers the aggregate and framework entities with its persistence
unit, and supplies a transaction-scoped `AggregatePersistenceContext` to `JpaAggregateRepository`.
The repository must be used only inside that persistence context and transaction.

JPQL is portable query language over entities and fields. Hibernate translates JPQL to the selected
database dialect for ordinary queries and updates. The Inbox first-claim operation is intentionally
different: it needs an atomic insert-or-ignore equivalent, so its database-specific strategy remains
explicit instead of pretending that JPQL can express it portably.

## Outbox And Inbox Stores

Select `jfoundry-outbox-jpa-spring-boot-starter` and/or
`jfoundry-inbox-jpa-spring-boot-starter` explicitly. The JPA adapters are framework-neutral; under
Spring Boot their framework entities are mapped automatically, so applications do not add them to
their own `@EntityScan`.

For Quarkus JPA Outbox storage, add `jfoundry-outbox-jpa-quarkus-runtime`. It registers
`JpaOutboxMessageEntity` with the default persistence unit and supplies a default CDI
`OutboxMessageStore`; an application-provided CDI `OutboxMessageStore` takes precedence. This is
storage assembly only: it does not add a dispatcher, scheduler, serializer, automatic event
externalization, or Inbox support. See [Quarkus](quarkus.md) for dependency setup and native-image
verification.

For Quarkus Outbox dispatching, add `jfoundry-outbox-quarkus-runtime` and provide a real CDI
`MessageSender`. Its scheduler is disabled until `jfoundry.outbox.dispatcher.enabled=true`; it does
not add a broker client or a fallback sender. See [Quarkus](quarkus.md) for dispatch configuration.

For Quarkus JPA Inbox storage, add `jfoundry-inbox-jpa-quarkus-runtime`. It registers
`JpaInboxMessageEntity` with the default persistence unit and supplies default CDI
`JpaInboxClaimStrategy`, `InboxMessageStore`, and `InboxTemplate` beans. The default claim strategy
supports PostgreSQL and MySQL only; declare `JpaInboxClaimStrategy` for another database. An
application-provided `JpaInboxClaimStrategy`, `InboxMessageStore`, or `InboxTemplate` takes
precedence. This is storage assembly only: it does not add a dispatcher, scheduler, serializer,
automatic event externalization, or a starter. See [Quarkus](quarkus.md) for dependency setup and
native-image verification.

For Helidon MP, add `jfoundry-outbox-jpa-helidon` to expose the JPA
`OutboxMessageStore`, and add `jfoundry-inbox-jpa-helidon` to expose
`JpaInboxClaimStrategy`, `InboxMessageStore`, and `InboxTemplate`. Both require the application's
JPA integration and migrations. Add `jfoundry-outbox-helidon` separately for opt-in
scheduled dispatch and automatic externalization. See [Helidon MP](helidon.md) for the CDI
alternative replacement rule and native-image scope.

Entity registration is not schema management. Copy the matching Outbox or Inbox SQL template into
the application's migration process and maintain it there. Do not rely on Hibernate schema
generation to create or evolve jfoundry tables.

The Outbox store reads a page of dispatchable candidates with JPQL and claims each record through a
compare-and-set update. A dispatcher claim token establishes ownership; publication and failure
updates for a claimed record use that token.

The JPA Inbox built-in atomic claim strategy supports PostgreSQL and MySQL only. Provide
`JpaInboxClaimStrategy` for another database product. When the product is unknown and no strategy
is supplied, a runtime default fails fast rather than choosing a generic dialect behavior. A
user-provided `InboxMessageStore`, `OutboxMessageStore`, `InboxTemplate`, or
`JpaInboxClaimStrategy` takes precedence.

A custom claim strategy implements
`boolean tryClaim(EntityManager entityManager, String messageId, String consumerName, String claimToken, Instant now)`.
It must atomically create the `PROCESSING` row with both the supplied token and claim time, and
return `false` for an existing delivery; include a concurrent duplicate-delivery test for the target
database. Raw JPA/Hibernate Outbox and Inbox assembly must apply the transaction boundaries described
by [reliable messaging](../capabilities/reliable-messaging.md).

For runtime assembly and configuration, see [Spring Boot](spring-boot.md), [Quarkus](quarkus.md),
[Helidon MP](helidon.md), and the [auto-configuration reference](../reference/spring-boot-autoconfiguration.md).
