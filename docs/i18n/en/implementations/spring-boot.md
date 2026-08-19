# Spring Boot Runtime Assembly

Spring Boot is a peer runtime integration for the framework-neutral jfoundry core. It uses Spring
Boot starters and conditional auto-configuration to assemble selected capabilities. It does not
make Spring APIs part of the domain or application model, and it does not imply that every
capability is enabled by the base starter.

## Assembly Model

Use `jfoundry-spring-boot-parent` as the Boot-only application's only Maven parent, then add
`jfoundry-spring-boot-starter` in the runtime assembly module. The parent inherits
`spring-boot-starter-parent:4.1.0`, sets Java 25, and imports `jfoundry-spring-boot-dependencies`
before `jfoundry-dependencies`. The base starter intentionally remains small: it provides general
Boot wiring and a Spring-backed `TransactionRunner`, but no persistence provider, broker, Outbox,
Inbox, JobRunr, or Redisson client.

An application that needs Spring Cloud or Spring Cloud Alibaba uses `jfoundry-spring-cloud-parent`.
That independent line inherits Spring Boot 4.0.7 and imports `jfoundry-spring-cloud-dependencies`
before `jfoundry-dependencies`; it manages Spring Cloud 2025.1.2 and Spring Cloud Alibaba
2025.1.0.0. Do not combine the two Spring runtime BOMs. Cloud Alibaba belongs only to the Cloud line.

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>1.0.3</version>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

An application that must retain a different parent can import the two JFoundry BOMs directly as
shown in [Getting Started](../integration/getting-started.md). It must then manage its Java and
Spring Boot parent configuration itself.

Add every other capability explicitly. This keeps a Spring Boot application honest about its
database, delivery, scheduling, and distributed-lock choices.

## Capability Composition

| Need | Add | Boundary |
|---|---|---|
| Local application transactions | `jfoundry-spring-boot-starter` | Supplies the Spring `TransactionRunner`; applications may replace it. |
| Local domain-event listeners | `jfoundry-domain-event-spring-boot-starter` | Publishes domain events through Spring application events; it is not an Outbox or broker. |
| Aggregate persistence with JPA | `jfoundry-persistence-jpa-spring-boot-starter` | One managed entity graph per aggregate; no Outbox or Inbox store. |
| Aggregate persistence with MyBatis-Plus | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | Business aggregate persistence only; no Outbox or Inbox store. |
| RFC 9457 Web MVC errors | `jfoundry-webmvc-spring-boot-starter` | HTTP inbound adapter only. |
| JSON serialization contract | `jfoundry-messaging-spring-boot-starter` | Adds Spring messaging integration and the default Jackson `PayloadSerializer`, but no real sender. |
| Kafka, RabbitMQ, or RocketMQ delivery | Matching `jfoundry-messaging-*-spring-boot-starter` | Select a concrete broker transport explicitly. |
| Outbox runtime | `jfoundry-outbox-spring-boot-starter` | Adds externalization and Spring scheduling integration; select a store and sender separately. |
| JPA or MyBatis-Plus Outbox store | Matching `jfoundry-outbox-*-spring-boot-starter` | Database store only; applications own migrations. |
| Inbox runtime and store | `jfoundry-inbox-spring-boot-starter` plus one `jfoundry-inbox-*-spring-boot-starter` | Consumer idempotency; applications own migrations. |
| JobRunr Outbox dispatch | `jfoundry-outbox-jobrunr-spring-boot-starter` | Optional dispatcher; still needs an Outbox store and real sender. |
| Redisson distributed lock | `jfoundry-lock-redisson-spring-boot-starter` | Optional cross-instance locking only. |

The exact starter catalog, configuration properties, conditions, and bean precedence are maintained
in the [Spring Boot auto-configuration reference](../reference/spring-boot-autoconfiguration.md).

## Transactions And Domain Events

Use the framework-neutral `TransactionRunner` for portable application boundaries. Spring maps that
contract to its transaction infrastructure and respects the six jfoundry propagation modes. A
Spring `@Transactional` boundary can also be appropriate when the application deliberately adopts
Spring semantics; do not layer independent transaction boundaries around the same use case without
a defined ownership rule. See [application transactions](../capabilities/application-transactions.md).

The event starter activates application-service domain-event dispatch and publishes each dispatched
event through Spring's `ApplicationEventPublisher`. An ordinary listener observes publication in
process. A `@TransactionalEventListener` selects the desired transaction phase, such as
`AFTER_COMMIT`; this is distinct from the Outbox path. Failed application-service invocations do
not dispatch their pending aggregate events. Aggregate behavior still explicitly records each domain
fact with `recordEvent(...)`. When persistence registers the aggregate inside an active Spring
transaction, the runtime dispatches its events in that transaction's `beforeCommit` phase. This makes
the aggregate change and any Outbox row atomic, while the Spring event adapter still publishes only
after commit. Without an active transaction, the runtime falls back to dispatching at the successful
outermost `@ApplicationService` boundary. Application business code does not call `drainEvents()` in
this automatic path.

## Persistence

The persistence starters are named after the capability they assemble, not merely the ORM they
pull in. `jfoundry-persistence-jpa-spring-boot-starter` assembles the JPA aggregate adapter,
Spring transaction-bound persistence context, and Spring Boot JPA runtime. Its MyBatis-Plus peer
does the same for MyBatis-Plus business aggregate persistence, including the default technical audit
handler for data objects that opt in through `AuditStampHolder`. The shared persistence auto-configuration supplies UTC audit
time and an empty actor provider; applications normally contribute `AuditActorProvider` from their
security integration.

Both are deliberately separate from reliable messaging stores. Add the matching Outbox or Inbox
starter only after the use case requires durable external publication or consumer idempotency.
Aggregate mapping, optimistic-locking, and repository-shape decisions remain in the
[JPA](jpa.md) and [MyBatis-Plus](mybatis-plus.md) implementation guides.

## Reliable Messaging

The Outbox starter provides transaction-aware recording, scheduled dispatch integration, recovery,
and cleanup according to its configured mode. It does not create a database table and it does not
invent a message destination. Copy the selected SQL template into the application's own migration
process.

`jfoundry-messaging-spring-boot-starter` does not register a fallback `MessageSender`. Before
enabling dispatch, add one broker-specific starter or provide an application `MessageSender`; without
one, no production delivery path exists. Automatic Outbox event recording is disabled by default;
enable it with `jfoundry.domain.event.dispatch.outbox.enabled=true`. It writes only an
`@Externalized` domain event or an event selected by `DomainEventExternalizer`, never a message
inferred from a persistence change. See [Message Delivery](../capabilities/message-delivery.md) for
direct broker selection and [reliable messaging](../capabilities/reliable-messaging.md) for Outbox
and Inbox semantics.

## Web, Locks, And Replacement

The Web MVC starter is an inbound adapter. It owns the shared RFC 9457 contract for supported
jfoundry exceptions, application `ProblemMapper` mappings, and `ProblemCatalog`-supported Spring MVC HTTP
failures; domain and application code must not select HTTP statuses directly. Other Spring MVC
failures retain Spring's original status and problem response. The auto-configuration runs before
Spring Boot's Web MVC problem-details configuration, so enabling `spring.mvc.problemdetails.enabled`
does not introduce a competing handler. It intentionally does not configure authentication or
authorization. A security adapter that owns those semantics can render its own `401` or `403`
descriptor with `ProblemDetailRenderer.render(...)`. The shared contract and capability-selection
entry point are documented in [Web](../capabilities/web.md).

`jfoundry-web-spring` is the opt-in Spring Web integration for outbound `RestClient` calls. Configure
only the builder that owns the integration with `RestClientSupport.configure(builder)`, then execute
the selected call through `RestClientSupport.execute(...)`. A non-success response becomes an
`HttpResponseException` containing only its status code. Transport and response-decoding failures
become an `HttpRequestException` with a safe failure kind. The default `BASIC` HTTP logging does not
access request or response bodies. Applications can select `NONE`, `HEADERS`, or `FULL` with
`RestClientSupport.configure(builder, HttpLoggingLevel)`; `FULL` logs redacted, size-limited JSON
bodies and can read an unconsumed error response body for diagnostics. The response error handler
itself does not read, copy, or retain a downstream response body; an application adapter that owns a
documented downstream protocol must perform any body parsing itself.

Redisson locking is optional. Use it only when a use case needs cross-instance coordination that
cannot be met by database constraints, idempotency, or local synchronization.

Auto-configured defaults are replaceable. Application beans take precedence for contracts such as
`TransactionRunner`, `PersistenceFailureTranslator`, `AggregatePersistenceContext`,
`MessageSender`, `PayloadSerializer`, Outbox/Inbox stores, and their store-specific strategies.

## Verification

The runtime-local integration profile validates Spring Boot assembly, starter dependency boundaries,
auto-configuration, and middleware paths against Testcontainers services, including MySQL, PostgreSQL,
Kafka, and RabbitMQ:

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pit verify
```

The same module also contains a minimal Spring Boot 4.1.0 AOT consumer. On GraalVM Native Image, the
`native` profile builds it and CI starts the executable, then verifies `GET /jfoundry/native/ready`
returns `ready`. This is the Native Image support claim for the base Spring Boot starter and Web MVC
assembly. It does not certify optional persistence, broker, lock, or scheduler adapters; each such
capability needs its own Native Image integration verification before it can be claimed as supported:

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative package
```

The `native-mybatis-plus` profile separately certifies the Spring Boot MyBatis-Plus persistence
starter on GraalVM Native Image. It starts PostgreSQL in the JVM test process, launches the generated
Native executable, and verifies an insert, reload, update, and reload of a business-defined
`AuditStampHolder`,
including automatic `createdAt`, `createdBy`, `lastModifiedAt`, and `lastModifiedBy` filling. This
claim applies to Spring Boot 4.1.0, MyBatis-Plus 3.5.17, and PostgreSQL only; it does not certify
JPA, brokers, Redisson, or JobRunr. It also verifies the built-in MyBatis-Plus Outbox and Inbox
stores through append, paginated claim, idempotent claim, and processed-state operations:

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative-mybatis-plus verify
```

The `native-redisson` profile separately certifies the Redisson 4.6.1 lock starter with Redis. It
starts Redis in the JVM test process, launches the generated Native executable, and verifies that
the JFoundry `LockExecutor` acquires and releases a distributed lock. The `native-jobrunr` profile
separately certifies JobRunr 8.7.1 Outbox dispatching with PostgreSQL. It launches the generated
Native executable, enables the JobRunr background server, and verifies that a persisted Outbox
message is scheduled and published. These profiles do not certify other Redis, JobRunr storage,
broker, or persistence combinations. Native applications must also register their own event payload
types for Spring AOT binding when those types are serialized by the application.

### CI-Aligned Local Verification

Run all Spring CI stages locally with Java 25, Docker, and GraalVM Native Image:

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh spring
```

Use `--stage middleware`, `--stage native`, `--stage native-mybatis-plus`, `--stage native-redisson`,
or `--stage native-jobrunr` to run one stage. The general
`scripts/verify-ci-matrix.sh` remains the Docker-free Java 25 baseline.
