# Technical Audit

Technical audit metadata belongs to a persistence snapshot, not to a domain entity or aggregate.
JFoundry therefore does not provide `Auditable`, `AuditableEntity`, or `AuditableAggregateRoot` in
the domain module.

`jfoundry-persistence-core` defines `AuditStamp`, `AuditStampHolder`, `AuditActorProvider`, and
`AuditStamping`. An audit stamp contains only `createdAt`, `createdBy`, `lastModifiedAt`, and `lastModifiedBy`.
Timestamps use `Instant`; actor fields contain a stable identifier only. When no actor is available,
the actor field is `null`; JFoundry does not synthesize an `unknown` actor.

`AuditStamping` receives a `Clock` and an `AuditActorProvider`. Insert stamping sets both creation
and modification metadata. Update stamping preserves creation metadata and changes only the last
modification metadata. Persistence adapters invoke update stamping only for an actual update.

## Runtime Assembly

Spring Boot provides a UTC `Clock`, an empty `AuditActorProvider`, and an `AuditStamping` through
`jfoundry-persistence-spring-boot-autoconfigure`. Any application `Clock`, `AuditActorProvider`, or
`AuditStamping` bean replaces the corresponding default. A security integration normally supplies
only `AuditActorProvider`; it owns authentication and maps its current principal to a stable actor
identifier.

`jfoundry-quarkus-runtime` and `jfoundry-helidon-runtime` provide the same UTC default through CDI.
An application `AuditActorProvider` is used when exactly one is available. Quarkus applications can
replace the default `AuditStamping` with a CDI bean. Helidon uses an enabled CDI alternative at
priority `1`; an application replacing the complete service must declare an enabled
`@Alternative` with a higher priority. Neither runtime has a JFoundry MyBatis-Plus integration.

## Jakarta Persistence

`jfoundry-persistence-jpa` provides `JpaAuditData` and `JpaAuditStamping`. Applications opt in by
constructing their `JpaAggregateRepository` with a `JpaAuditStamping` backed by the application's
`AuditStamping`. The repository stamps derived `JpaAuditData` entities before persist and update;
it does not rely on JPA listener dependency injection or a global audit context.

## MyBatis-Plus

`jfoundry-persistence-mybatis-plus` provides `MybatisPlusAuditMetaObjectHandler`. The Spring Boot
MyBatis-Plus persistence starter registers the handler from the configured `AuditStamping` unless
the application already has a `MetaObjectHandler`. Outside that Spring Boot assembly, register the
handler with an application configured `AuditStamping`. The handler applies only to data objects
that implement `AuditStampHolder`; applications own their fields and MyBatis-Plus mapping annotations.

Business-significant submitters, approvers, and similar facts remain explicit domain state or domain
events. Soft deletion and append-only compliance audit trails are separate capabilities.
