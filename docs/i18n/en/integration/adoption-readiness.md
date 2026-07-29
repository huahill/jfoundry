# Adoption Readiness and Validated Scope

This document records a point-in-time assessment of whether jfoundry and the related domain
architecture plugin can support real business application development. It is an evidence-based
adoption guide, not a general production certification. The assessment date is 2026-07-15.

## Related Repositories

| Repository | Role |
|------------|------|
| [xfoundries/jfoundry](https://github.com/xfoundries/jfoundry) | Optional Java runtime and implementation framework for DDD-oriented business applications |
| [xfoundries/domain-architecture-skills](https://github.com/xfoundries/domain-architecture-skills) | Design-time plugin for domain modeling, architecture decisions, and optional jfoundry landing |
| [xfoundries/jfoundry-expense-approval-demo](https://github.com/xfoundries/jfoundry-expense-approval-demo) | End-to-end validation project using a deliberately small business domain and a complete integration path |

The plugin and framework have different responsibilities. The plugin guides decisions and remains
useful when a project does not select jfoundry. jfoundry is an optional implementation choice after
the domain, architecture, project shape, and runtime constraints are understood.

## Current Assessment

| Project | Current assessment | Recommended position |
|---------|--------------------|----------------------|
| `domain-architecture-skills` | Ready for use in real projects as design-time guidance | Standard domain and architecture workflow for AI-assisted development, with human review of business meaning |
| jfoundry | Suitable for controlled adoption in real projects within the validated stack | Optional business framework, pinned to an immutable version and subject to project production gates |
| Domain Architecture plugin plus optional jfoundry landing | Proven as an end-to-end sequence from requirements and modeling through architecture selection, optional framework landing, implementation, and acceptance, with separately maintained Hexagonal and Onion Simple variants | Preferred first for Java 25, Spring Boot, MyBatis-Plus, PostgreSQL, Kafka, and Redis projects |

The last row means this concrete sequence:

```text
business requirements
  -> framework-neutral domain modeling by the Domain Architecture plugin
  -> explicit architecture and project-shape decision by the plugin
  -> optional using-jfoundry implementation guidance, only when jfoundry is selected
  -> business implementation using the selected runtime and adapters
  -> architecture, integration, and end-to-end acceptance tests
```

It is not a separate external workflow product, and it does not make jfoundry mandatory. The plugin
owns the design-time decisions; jfoundry supplies optional implementation contracts and adapters
after those decisions.

This assessment supports real project use. It does not claim that either project is a universal
architecture, that jfoundry should be mandatory, or that every supported-looking technology
combination has been production-validated.

## Value Provided

### jfoundry

jfoundry is more than a project skeleton. Its main value is that it gives recurring business
application concerns explicit boundaries and reusable contracts:

- Runtime-neutral domain and application contracts keep Spring, ORM, HTTP, and broker APIs outside
  the business core.
- jMolecules semantics and ArchUnit rules make selected DDD, Hexagonal, Onion, and CQRS constraints
  executable instead of leaving them only in package names or documents.
- Transaction boundaries, aggregate persistence lifecycle, optimistic concurrency support,
  persistence failure translation, domain events, Outbox, Inbox, messaging SPI, and distributed
  locks reduce repeated infrastructure design.
- Capability-specific modules and starters keep persistence, brokers, Outbox, Inbox, and locking
  optional.
- Composite aggregate persistence remains explicit in the business adapter when dependent-table
  synchronization is domain-specific. The framework does not infer aggregate restoration or hide
  it behind reflection.

### Domain Architecture Plugin

The plugin addresses a different source of risk: an AI agent can generate structurally plausible
code while making the wrong domain or architecture decision. Its value is to keep the sequence and
ownership of decisions explicit:

- Requirements and business language lead to domain modeling before framework landing.
- DDD discipline, architecture-style constraints, framework conventions, heuristics, and project
  policies are identified separately.
- Hexagonal, Onion, Layered, and CQRS are selected only when the context justifies them.
- Simple CRUD can remain simple, and jfoundry remains optional.
- Phase results and handoffs preserve assumptions, blockers, evidence, and open questions for later
  planning, implementation, and review.

Architecture guidance cannot discover missing business truth or replace accountable human review.
Architecture tests can verify declared static boundaries, but they cannot prove that aggregates,
invariants, or bounded contexts model the business correctly.

## Validation Evidence

The [expense approval demo](https://github.com/xfoundries/jfoundry-expense-approval-demo)
was intentionally kept light in business complexity while exercising a complete architecture and
integration path. The same business rules, database model, integration contracts, and acceptance
scenarios were validated with separately maintained Hexagonal and Onion Simple variants. At the
assessment date, the recorded evidence includes:

- jfoundry test matrices on Java 21 and Java 25 across the 67-module reactor.
- Validation of every shipped plugin skill, the Codex plugin manifest, and the Claude marketplace
  metadata.
- Both architecture variants passed the same complete automated demo suite, including five
  container-based end-to-end scenarios.
- Two independent PostgreSQL databases, Kafka, Redis, and two Spring Boot application contexts in
  the end-to-end environment.
- Testcontainers verification of the JPA and MyBatis-Plus Outbox/Inbox stores on both PostgreSQL
  and MySQL, including database-specific JPA Inbox claiming.
- Payment success and failure, duplicate message delivery through Inbox, concurrent monthly-limit
  enforcement, and transaction rollback without an approval Outbox record.
- A separately executed local path from HTTP approval through Kafka to the final `PAID` projection.
- Onion validation of explicit Domain, Application, and Infrastructure rings, inward dependency
  rules, DDD repository placement, responsibility-first application contract names, and the same
  targeted CQRS structure used by the Hexagonal variant.

| Validation subject | Evidence | Does not imply |
|--------------------|----------|----------------|
| Hexagonal variant (`main`) | Explicit Primary/Secondary Port and Adapter roles, capability-nested direction packages, neutral application-owned shared views, strict ArchUnit rules | That Hexagonal is the default architecture or that every application needs all roles |
| Onion Simple variant (`onion-architecture`) | Explicit Domain/Application/Infrastructure rings, inward dependencies, DDD-first and responsibility-first names, the same acceptance suite | That Onion defines Port/Adapter semantics or that package-level rings provide Maven module isolation |
| Shared business baseline | The same aggregate behavior, database model, integration contracts, CQRS, Outbox/Inbox, locking, middleware topology, and acceptance scenarios | That the styles are interchangeable, should be mixed, or have equal organizational trade-offs in every project |

The demo also exposed framework and guidance defects instead of merely confirming the initial
design. The resulting fixes covered aggregate persistence tracking, optimistic concurrency,
exception boundaries, Outbox integration contracts, PostgreSQL Inbox idempotency, portable JSON,
broker selection, auto-configuration ordering, Spring AOP proxy infrastructure, architecture-style
semantics, capability-first package guidance, and separation of architecture-neutral CQRS rules
from Hexagonal Port/Adapter conventions.

## Validated Scope

The strongest evidence currently applies to:

- Java 25 business applications.
- Spring Boot 4.0.x and Spring Framework 7.0.x.
- Quarkus 3.37.3 CDI discovery and Jakarta Transactions `TransactionRunner` integration, with a
  JVM consumer smoke test and a Native Image CI gate.
- Helidon MP 4.5.1 CDI/JTA, JPA assembly, JPA Outbox/Inbox stores, scheduling, and JAX-RS Problem
  Details. Its GraalVM 25 consumer verifies CDI/Web startup and the Problem Details response; Native
  JTA and JPA remain upstream experimental limitations, with a reproducible failure record in
  [Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).
- MyBatis-Plus business persistence with PostgreSQL.
- JPA and MyBatis-Plus Outbox/Inbox stores with PostgreSQL and MySQL.
- Kafka integration, Redis/Redisson distributed locking, transactional Outbox, and consumer Inbox.
- DDD modeling with either separately validated Hexagonal Architecture or Onion Simple
  Architecture, plus targeted CQRS without Event Sourcing.
- Multi-module Maven applications with separate integration contracts and runtime assemblies.

Lack of evidence for another runtime does not block adoption in this validated stack. It means that
the conclusion must not be generalized to that runtime without equivalent tests.

## Boundaries and Risks

The following areas are not established by the current evidence:

- Compile-time isolation when Onion rings are split into separate Maven modules. The validated
  Onion variant uses package-level rings inside one application module and ArchUnit dependency
  rules.
- The migration cost or organizational outcome of changing architecture style in an established
  production codebase. The demo validates two maintained variants, not a production migration.
- Helidon broker delivery, Redisson locking, JobRunr, and working Helidon Native JTA/JPA paths. Do
  not infer these from Spring or Quarkus capabilities. Micronaut has no runtime integration yet.
- Other ORM, database, or broker combinations.
- Application security, observability, deployment, capacity, performance, disaster recovery, and
  long-running production operations.
- Compatibility across a real downstream upgrade from one stable jfoundry release to another.

At the assessment date, the reactor version is `1.0.0-SNAPSHOT` and the repository has release
automation and a documented [compatibility matrix](../../../release/compatibility.md), but no stable
release tag. A production application should not depend on a moving SNAPSHOT; it should consume a
released version or an immutable, internally governed build.

The domain architecture plugin has structured workflows, references, templates, and format
validation, but it does not yet have an automated scenario evaluation suite covering good and bad
agent decisions. The expense approval demo supplies strong integration evidence for two
architecture variants over one business and runtime path, not a complete behavioral benchmark for
the plugin.

Spring Framework 7 also plans to remove convention-based composed-annotation attribute overrides.
The upstream jMolecules discussion remains open in
[xmolecules/jmolecules#153](https://github.com/xmolecules/jmolecules/issues/153). jfoundry should not
introduce Spring `@AliasFor` into runtime-neutral architecture modules as a compatibility shortcut.

## Adoption Guidance

For a real business project:

1. Use the domain architecture plugin for requirements-to-model and architecture decisions, while
   keeping business review accountable to the project team.
2. Select jfoundry only after the architecture, runtime, persistence, messaging, and project shape
   make it useful. Do not make framework adoption a prerequisite for domain modeling.
3. Start with a medium-criticality business service before standardizing jfoundry across an
   organization or placing it underneath the most critical financial path.
4. Pin an immutable jfoundry version through the appropriate BOM. Do not ship against a moving
   SNAPSHOT.
5. Re-run the complete application acceptance path with the exact database, broker, deployment
   topology, and failure policies selected by the project.
6. Add project-owned gates for authentication and authorization, secrets, audit, metrics, tracing,
   health checks, alerting, migration and rollback, backup and recovery, load, and fault injection.
7. Keep escape hatches explicit: jfoundry persistence bases are optional, adapters can implement
   ports directly, and low-complexity areas can use simpler application patterns.

## Decision

Within the validated stack, jfoundry and the domain architecture plugin are sufficient to support
AI-assisted development of a complete DDD-oriented business application using either of the
separately validated Hexagonal or Onion Simple architecture styles, with targeted CQRS and reliable
messaging. This does not make the styles interchangeable or remove the need for an explicit
architecture decision. The plugin is ready to act as a real-project architecture governance tool.
jfoundry is ready for controlled real-project adoption, but a stable immutable release and
project-specific non-functional verification are still required before treating it as an
organization-wide default production framework.

Update this assessment when a stable release is published, a downstream upgrade is proven, another
runtime, infrastructure combination, or architecture style receives equivalent evidence, or the
production-readiness gates materially change.
