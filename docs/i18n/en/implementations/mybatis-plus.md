# MyBatis-Plus Implementation

This implementation satisfies the [aggregate persistence](../capabilities/aggregate-persistence.md)
and [reliable messaging](../capabilities/reliable-messaging.md) contracts with MyBatis-Plus.

## Aggregate Persistence

Persistence data objects extend `AggregateData<K>` and use database-friendly IDs. A
`DataMapper` converts IDs, data, and aggregates at the repository boundary. The complete default is
one `AggregateData`, one `DataMapper`, and one `BaseMapper` through
`MybatisPlusAggregateRepository`.

For a multi-table aggregate with a MyBatis-Plus root record, override the complete `do*` operation
and use `loadAggregate`, `insertAggregate`, `updateAggregate`, and `deleteAggregate` when their
root persistence and context behavior fit. Otherwise extend `AbstractAggregateRepository`. The
business adapter remains responsible for restoration and dependent-record synchronization.

For persistence-owned optimistic locking, annotate the root data version with `@Version`, configure
`OptimisticLockerInnerInterceptor`, and pass the data class to `MybatisPlusAggregateRepository`.
The repository handles loaded-version tracking, `updateById` version restoration, zero-row
conflicts, tracked-version advancement, and ID-plus-version removal. Data without `@Version`
retains non-tracked behavior.

Use Lambda Wrappers for ordinary single-table predicates, ordering, updates, and deletes. Keep
explicit SQL where one atomic statement or database-specific behavior is required, such as a
compare-and-set update.

### Direct MyBatis-Plus Assembly

The MyBatis-Plus adapter is runtime-neutral, not a turnkey runtime bootstrap. Outside Spring Boot,
the application configures MyBatis-Plus, starts and completes each transaction, and owns mapper
registration. When `@Version` is configured, it must also supply a transaction-scoped
`AggregatePersistenceContext` to `MybatisPlusAggregateRepository`; that context retains the loaded
version used by later update and delete operations. Repositories without `@Version` do not retain
version state, but their database operations still belong inside the application's transaction.

Spring Boot assembly is available through
`jfoundry-persistence-mybatis-plus-spring-boot-starter`. Quarkus does not currently provide a
jfoundry MyBatis-Plus runtime integration; do not infer one from the runtime-neutral adapter. See
[Spring Boot](spring-boot.md) for the supported assembly and [Quarkus](quarkus.md) for its current
capability scope.

For data objects derived from `MybatisPlusAuditData`, this Spring Boot starter also registers
`MybatisPlusAuditMetaObjectHandler`. It uses the runtime's `AuditStamping` and backs off when the
application provides any `MetaObjectHandler`.

## Outbox And Inbox Stores

Choose `jfoundry-outbox-mybatis-plus-spring-boot-starter` for the built-in `OutboxMessageStore` and
`jfoundry-inbox-mybatis-plus-spring-boot-starter` for the built-in `InboxMessageStore`. These are
explicit choices; `jfoundry-persistence-mybatis-plus-spring-boot-starter` assembles business persistence only
and does not add either store. SQL templates remain application-owned migrations.

Under Spring Boot, the default Outbox dispatcher and Inbox template use `TransactionRunner` for
their database phases. This is required even though individual MyBatis mapper calls can otherwise
commit independently: the handler and Inbox `PROCESSED` transition must remain atomic.

For runtime assembly and user replacement behavior, see [Spring Boot](spring-boot.md). The
[reference](../reference/spring-boot-autoconfiguration.md) lists conditions and properties.
