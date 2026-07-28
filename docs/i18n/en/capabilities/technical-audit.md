# Technical Audit

Technical audit metadata belongs to a persistence snapshot, not to a domain entity or aggregate.
JFoundry therefore does not provide `Auditable`, `AuditableEntity`, or `AuditableAggregateRoot` in
the domain module.

`jfoundry-persistence-core` defines `AuditStamp`, `AuditActorProvider`, and `AuditStamping`. An
audit stamp contains only `createdAt`, `createdBy`, `lastModifiedAt`, and `lastModifiedBy`.
Timestamps use `Instant`; actor fields contain a stable identifier only. When no actor is available,
the actor field is `null`; JFoundry does not synthesize an `unknown` actor.

`AuditStamping` receives a `Clock` and an `AuditActorProvider`. Insert stamping sets both creation
and modification metadata. Update stamping preserves creation metadata and changes only the last
modification metadata. Persistence adapters invoke update stamping only for an actual update.

## MyBatis-Plus

`jfoundry-persistence-mybatis-plus` provides `MybatisPlusAuditData` and
`MybatisPlusAuditMetaObjectHandler`. Register the handler with an `AuditStamping` configured by the
application. The handler only applies to data objects derived from `MybatisPlusAuditData`; it does
not infer audit fields from arbitrary MyBatis-Plus data objects.

## Jakarta Persistence

`jfoundry-persistence-jpa` provides `JpaAuditData` and `JpaAuditStamping`. Applications opt in by
constructing their `JpaAggregateRepository` with a `JpaAuditStamping` backed by the application's
`AuditStamping`. The repository stamps derived `JpaAuditData` entities before persist and update;
it does not rely on JPA listener dependency injection or a global audit context.

Business-significant submitters, approvers, and similar facts remain explicit domain state or domain
events. Soft deletion and append-only compliance audit trails are separate capabilities.
