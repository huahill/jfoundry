# Aggregate Persistence

Aggregate persistence preserves the aggregate boundary while allowing a business project to choose
its persistence technology. The domain model is not a persistence schema: annotations, type
handlers, logical-delete fields, and generated identifiers stay outside the aggregate.

## Contract

`JpaAggregateMapper` is the JPA boundary: it restores an aggregate from the managed entity and
applies current state back to that same instance. `MybatisPlusDataMapper` is the MyBatis-Plus
boundary: it converts an aggregate to the row object that MyBatis-Plus inserts or updates. Neither
contract is shared by the other technology. A mapper can be plain Java, MapStruct-generated where
that is sufficient, or a dependency-injected component when the application needs it.

The persistence adapter owns the complete aggregate operation. It must not offer a generic
multi-table collection synchronization policy: replacement, differential updates, append-only
writes, cascades, and deletion order are business decisions. A business adapter owns dependent-row
synchronization and uses a project-specific atomic deletion strategy when database constraints need
it.

## Transaction And Failure Semantics

`AggregatePersistenceContext` tracks persistence-owned state for one runtime-managed transaction.
Tracked operations fail fast when called outside that transaction or for an aggregate not loaded in
it. Detached aggregate merge is not a supported lifecycle.

`PersistenceFailureTranslator` is a runtime-neutral SPI. A runtime integration may translate
known availability failures to `ExternalAccessException`; integrity, locking, SQL, mapper, and
unknown failures retain their original meaning unless a business adapter can identify a genuine
business conflict.

## Choose An Implementation

| Need | Guide |
|------|-------|
| One JPA-managed entity graph per aggregate and JPA optimistic locking | [JPA](../persistence/jpa.md) |
| MyBatis-Plus data objects, repositories, wrappers, and optimistic locking | [MyBatis-Plus](../persistence/mybatis-plus.md) |

For Spring Boot runtime assembly and user bean overrides, see [Spring Boot](../runtimes/spring-boot.md).
The starter catalog, properties, and condition lookup are in [Spring Boot Auto-configuration](../reference/spring-boot-autoconfiguration.md).
