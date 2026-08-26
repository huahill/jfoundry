# Quarkus Runtime Integration

`jfoundry-quarkus-runtime` is a Quarkus extension that exposes the framework-neutral
`TransactionRunner` as a CDI bean. It keeps Quarkus, CDI, Jakarta Transactions, and GraalVM types
outside the domain, application, and infrastructure modules.

Its transaction, JTA domain-event coordination, and JAX-RS HTTP logging reuse the portable
`jfoundry-transaction-jta`, `jfoundry-domain-event-jta`, and `jfoundry-web-jaxrs` implementations.
Quarkus-owned runtime classes remain the public CDI/provider entry points, while deployment modules
retain Arc registration, augmentation, RESTEasy Reactive integration, and Native Image behavior.
Applications select the Quarkus runtime modules rather than assembling these shared implementation modules.

## Dependency Setup

Import the Quarkus BOM and the core JFoundry BOM with the same JFoundry version, then add the
runtime extension. `jfoundry-quarkus-dependencies` manages Quarkus platform ecosystem versions only;
it does not manage JFoundry module versions. The deployment artifact is discovered by Quarkus from the
runtime extension descriptor; applications must not add it directly.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-quarkus-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-quarkus-runtime</artifactId>
    </dependency>
</dependencies>
```

The extension brings Quarkus Arc and Narayana JTA as runtime dependencies. It registers one
application-scoped `QuarkusTransactionRunner`, which can be injected through the framework-neutral
`TransactionRunner` contract.

## Spring Boot And Quarkus Composition

Spring Boot starters select dependency sets and rely on Boot auto-configuration. Quarkus applications
compose explicit extensions; Quarkus discovers each matching deployment artifact from its runtime
artifact automatically.

| Spring Boot capability | Quarkus dependency composition |
|---|---|
| Spring Boot runtime baseline | `jfoundry-quarkus-runtime` |
| `jfoundry-domain-event-spring-boot-starter` | `jfoundry-quarkus-runtime` |
| `jfoundry-persistence-jpa-spring-boot-starter` | `jfoundry-quarkus-runtime`, `jfoundry-persistence-jpa`, `jfoundry-persistence-jpa-quarkus-runtime`, `quarkus-hibernate-orm`, and the selected Quarkus JDBC extension |
| `jfoundry-outbox-jpa-spring-boot-starter` | The JPA composition above plus `jfoundry-outbox-jpa-quarkus-runtime` and `jfoundry-outbox-quarkus-runtime` when dispatching is required |
| `jfoundry-inbox-jpa-spring-boot-starter` | The JPA composition above plus `jfoundry-inbox-jpa-quarkus-runtime` |
| Kafka or RabbitMQ messaging starter | `jfoundry-messaging-kafka-quarkus-runtime` or `jfoundry-messaging-rabbitmq-quarkus-runtime` |
| `jfoundry-webmvc-spring-boot-starter` | `jfoundry-web-quarkus-runtime` |

## Supported Scope

Quarkus is not a Spring starter translation layer. Its explicit composition currently covers CDI/JTA
transactions, local CDI domain-event delivery, JPA aggregate persistence, JPA Outbox and Inbox
stores, Outbox dispatch and maintenance, Kafka and RabbitMQ delivery, RFC 9457 Problem Details, and
safe inbound and MicroProfile REST Client diagnostic logging. The generic
`jfoundry-web-quarkus-runtime` extension owns the Quarkus REST boundary without moving Web lifecycle
APIs into the core.

MyBatis-Plus aggregate persistence, RocketMQ delivery, Redisson distributed locks, and JobRunr
assembly are not supported Quarkus compositions today. Do not add the framework-neutral adapters or
Spring starters as a substitute; select a custom application adapter only when the project owns that
integration.

## Transaction Semantics

The adapter maps all six `TransactionPropagation` values to Jakarta Transactions behavior:

| jfoundry propagation | Quarkus/Jakarta behavior |
|----------------------|--------------------------|
| `REQUIRED` | Joins an active transaction or starts one. |
| `REQUIRES_NEW` | Suspends an active transaction, starts a new transaction, then resumes it. |
| `SUPPORTS` | Joins an active transaction or runs without one. |
| `MANDATORY` | Requires an active transaction. |
| `NOT_SUPPORTED` | Suspends an active transaction and runs without one. |
| `NEVER` | Runs only when no transaction is active. |

Callback exceptions roll back an owned transaction. When the adapter joins an existing transaction,
callback exceptions mark that transaction rollback-only and preserve the original exception.

`TransactionOptions.timeout` maps to the Jakarta transaction timeout for the transaction started by
the adapter and restores the default afterwards. Jakarta Transactions has no portable transaction
name or read-only transaction setting, so this adapter rejects `TransactionOptions.name` and
`TransactionOptions.readOnly` rather than silently ignoring them.

## Domain Event Dispatch

The base runtime extension also provides the application-service event boundary. For every CDI bean
annotated with framework-neutral `@ApplicationService`, Quarkus adds a runtime-only interceptor
binding during augmentation. On the outermost successful invocation, the interceptor drains events
from aggregates registered through `DomainEventContext` and sends them to every CDI
`DomainEventDispatcher`. Nested application-service invocations share the same
scope, so dispatch occurs once at the outermost boundary. An exception escaping that boundary
discards its pending events.

```java
@ApplicationScoped
@ApplicationService
class ConfirmOrder {

    private final DomainEventContext domainEventContext;

    ConfirmOrder(DomainEventContext domainEventContext) {
        this.domainEventContext = domainEventContext;
    }

    void handle(Order order) {
        order.confirm();
        domainEventContext.register(order);
    }
}
```

The extension supplies the `DomainEventContext` used by this boundary. This assembly supports
synchronous application-service methods only; `CompletionStage` and Mutiny return types are rejected.
It provides in-process domain-event orchestration only and does not add an Outbox store, serializer,
broker client, or automatic event externalization.

## JPA Aggregate Persistence

To use `JpaAggregateRepository`, add `jfoundry-persistence-jpa`,
`jfoundry-persistence-jpa-quarkus-runtime`, and the Quarkus Hibernate ORM and datasource extensions
selected by the application. The JPA capability translates known Hibernate connection and query-timeout
failures into `ExternalAccessException`; applications may replace its CDI `PersistenceFailureTranslator`.
A repository subclass must be a CDI bean and
receive `EntityManager` through its constructor. The jfoundry extension discovers CDI beans that
implement `AggregatePersistenceContextAware` and supplies a JTA transaction-bound persistence
context automatically. An application may replace that default by declaring its own CDI
`AggregatePersistenceContext` bean.

Keep `findById(...)`, domain behavior, and `modify(...)` in the same `TransactionRunner` callback.
Quarkus binds the injected `EntityManager` and aggregate persistence state to that transaction, so
the repository applies changes to the entity graph loaded in that same persistence context.

```java
transactionRunner.run(() -> {
    Order order = repository.findById(orderId);
    order.confirm();
    repository.modify(order);
});
```

This assembly covers business aggregate persistence only. Add the explicit JPA Outbox capability
described below when an application needs a JPA-backed Outbox store.

## JPA Outbox Storage

Add `jfoundry-outbox-jpa-quarkus-runtime` alongside the base runtime extension and Quarkus Hibernate
ORM:

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-outbox-jpa-quarkus-runtime</artifactId>
</dependency>
```

The capability registers `JpaOutboxMessageEntity` with the default persistence unit and provides a
default CDI `OutboxMessageStore` backed by `JpaOutboxMessageStore`. An application can replace that
store by declaring its own CDI `OutboxMessageStore` bean. As with every jfoundry SQL template, the
application remains responsible for managing the `jfoundry_outbox_event` table through its migration
process.

This capability assembles persistence only. Add the explicit Outbox runtime assembly described
below for dispatching, payload serialization, or automatic domain-event externalization.

## Outbox Dispatching And Maintenance

Add `jfoundry-outbox-quarkus-runtime` when an application needs the shared Outbox claim, send, and
state-transition runtime:

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-outbox-quarkus-runtime</artifactId>
</dependency>
```

The extension provides a default CDI `OutboxDispatcher` and uses the Quarkus Scheduler. It remains
inactive unless `jfoundry.outbox.dispatcher.enabled=true`. The application must provide both an
`OutboxMessageStore` (for example through `jfoundry-outbox-jpa-quarkus-runtime`) and a real
`MessageSender`; the dispatcher does not add a broker client or a logging sender. Configure
`jfoundry.outbox.dispatcher.interval` (default `5s`), `batch-size` (default `50`), `max-retries`
(default `5`), `backoff-base` (default `1s`), and `backoff-max` (default `5m`) as needed. An
application-provided CDI `OutboxDispatcher` takes precedence.

Message delivery remains outside database transactions. Each claim and state transition runs in an
independent transaction through `TransactionRunner`, consistent with the framework-neutral Outbox
contract.

The same extension also provides scheduled Outbox maintenance without requiring a `MessageSender`.
Recovery is disabled by default; enable it with `jfoundry.outbox.recovery.enabled=true` to reset
stale `DISPATCHING` records at `jfoundry.outbox.recovery.interval` (default `60s`) after
`jfoundry.outbox.recovery.stuck-timeout` (default `5m`). Cleanup is independently disabled by
default; enable it with `jfoundry.outbox.cleanup.enabled=true` to remove expired terminal records
at `jfoundry.outbox.cleanup.interval` (default `24h`). Its defaults retain `PUBLISHED` records for
seven days, `DEAD_LETTERED` records for 30 days, and delete at most 1000 records per status per
run. Configure `published-retention-days`, `dead-lettered-retention-days`, and `batch-size` under
`jfoundry.outbox.cleanup` when different operational limits are required.

Recovery and each terminal-status cleanup run use independent `REQUIRES_NEW` transaction boundaries.
Broker adapters and starters remain explicit capabilities.

## Kafka Message Delivery

Add `jfoundry-messaging-kafka-quarkus-runtime` to provide the default Quarkus Kafka implementation
of `MessageSender`:

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-messaging-kafka-quarkus-runtime</artifactId>
</dependency>
```

The extension brings `quarkus-messaging-kafka` and sends through the fixed outgoing channel
`jfoundry-kafka`. Configure that channel with the SmallRye Kafka connector:

```properties
kafka.bootstrap.servers=localhost:9092
mp.messaging.outgoing.jfoundry-kafka.connector=smallrye-kafka
mp.messaging.outgoing.jfoundry-kafka.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.jfoundry-kafka.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

`MessageSender.send(topic, payloadKey, payload)` dynamically sets the Kafka topic and key for each
record, so `@Externalized` and `@AggregateRouting` continue to determine Outbox routing. The channel
name is infrastructure configuration, not a business destination. The adapter waits for broker
acknowledgement and maps failures to `SendResult`. Configure delivery timeouts through the Kafka
client and connector properties. It is a Quarkus CDI default bean, so an application can replace it
with its own `MessageSender`.

## RabbitMQ Message Delivery

Add `jfoundry-messaging-rabbitmq-quarkus-runtime` for a default RabbitMQ `MessageSender`:

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-messaging-rabbitmq-quarkus-runtime</artifactId>
</dependency>
```

The adapter uses the Vert.x RabbitMQ client and connects only when the first message is sent.
`MessageSender.send(topic, payloadKey, payload)` maps `topic` to the exchange and `payloadKey` to
the routing key. Configure the client with a Quarkus `@Identifier("jfoundry-rabbitmq")`
`RabbitMQOptions` producer; its standard Vert.x options cover host, credentials, TLS, recovery, and
connection timeouts. The CDI default bean is replaceable with an application `MessageSender`.

## Automatic Domain-Event Externalization

`jfoundry-outbox-quarkus-runtime` also supplies an explicit automatic externalization assembly. It
adds Quarkus Jackson and produces replaceable defaults for `PayloadSerializer`,
`ExternalizationRuleResolver`, `AggregateRoutingResolver`, `OutboxTemplate`, and
`DomainEventOutboxRecorder`. It does not add an Outbox store or a broker client; add a store
capability such as `jfoundry-outbox-jpa-quarkus-runtime` separately.

Automatic recording is disabled by default. Enable it only when the domain event itself is a stable
integration contract:

```properties
jfoundry.domain.event.dispatch.outbox.enabled=true
```

Mark each intended integration event with `@Externalized("<topic>")`. Add `@AggregateRouting` when
the aggregate type, id, or version should be retained with the Outbox row; the resolved aggregate id
also becomes the default message key when no routing key is specified. Events without
`@Externalized` are not recorded. Applications can replace the default serializer or recorder with
their own CDI bean.

When an aggregate is registered while a JTA transaction is active, automatic externalization records
the Outbox entry in that transaction's `beforeCompletion` phase. The aggregate change and Outbox row
are therefore atomic even when an application service creates its boundary with `TransactionRunner`.
Local CDI domain-event observers remain separate and are notified only after a successful commit.

The extension registers `@Externalized` event classes for Jackson reflection during augmentation, so
the default serializer works in Native Image. It does not prescribe a broker transport; use an
explicit `MessageSender` adapter and enable the dispatcher separately when delivery is required.

## JPA Inbox Storage

Add `jfoundry-inbox-jpa-quarkus-runtime` alongside the base runtime extension and Quarkus Hibernate
ORM:

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-inbox-jpa-quarkus-runtime</artifactId>
</dependency>
```

The capability registers `JpaInboxMessageEntity` with the default persistence unit. It provides
default CDI beans for `JpaInboxClaimStrategy`, `InboxMessageStore`, and `InboxTemplate`; the store
uses `JpaInboxMessageStore`, and the template uses the runtime's `TransactionRunner` for its claim,
processing, and failure boundaries. The built-in claim strategy is selected from the datasource
product and supports PostgreSQL and MySQL only. For another database, declare a CDI
`JpaInboxClaimStrategy` bean. An application may also replace `InboxMessageStore` or
`InboxTemplate` with its own CDI bean.

The application remains responsible for copying the Inbox SQL template into its migration process
and maintaining the `jfoundry_inbox_message` table. This capability assembles persistence only. It
does not provide a dispatcher, scheduler, serializer, automatic event externalization, or a starter.

## Problem Details (RFC 9457)

Add `jfoundry-web-quarkus-runtime` when a Quarkus REST application needs the shared RFC 9457 error
contract. The same extension also provides the HTTP diagnostic logging described below. The
runtime-neutral contract and the dependency choices for all
supported runtimes are in [Web](../capabilities/web.md):

```xml
<dependency>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-web-quarkus-runtime</artifactId>
</dependency>
```

To map Bean Validation request failures to the shared validation problem, also add the optional
Quarkus validation capability:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-validator</artifactId>
</dependency>
```

The extension brings Quarkus REST Jackson support and renders `application/problem+json` responses
for the six JFoundry application and domain exceptions: `InvalidArgumentException`,
`NotFoundException`, `ConflictException`, `ExternalAccessException`,
`DomainRuleViolationException`, and `DomainStateException`. It also renders the shared contract for
standard Jakarta REST failures with statuses `400`, `404`, `405`, `406`, `413`, `415`, and `503`.

Responses contain the shared `type`, `title`, `status`, and `detail` fields; `type` is the stable
machine-readable problem identifier. The adapter preserves non-entity headers supplied by the source
Jakarta REST response, including `Allow` when it is present. It does not infer headers that Quarkus
does not provide. Unknown exceptions and other HTTP statuses retain normal Quarkus behavior instead
of being converted into a JFoundry error.

When `quarkus-hibernate-validator` is present, the deployment processor registers a mapper for
Quarkus REST request validation. The mapper renders `urn:jfoundry:problem:request-validation` with
the shared `errors[].detail` and optional `errors[].pointer` members. It never accesses or returns a
rejected value. Return-value validation failures are rethrown so they retain Quarkus server-error
handling instead of being mislabeled as invalid client input.

The extension does not configure security. A Quarkus security adapter that owns authentication and
authorization can render its own `401` or `403` descriptor with the public
`ProblemDetailsRenderer.render(...)` API.

## HTTP Diagnostic Logging

`jfoundry-web-quarkus-runtime` registers a Quarkus REST request/response filter and reader/writer
interceptors. Configure inbound logging with `jfoundry.web.quarkus.logging-level`; it defaults to
`NONE`. `BASIC`, `HEADERS`, and `FULL` require the
`org.jfoundry.http.quarkus.HttpLoggingProvider` category at `DEBUG`.

When the application selects a Quarkus MicroProfile REST Client extension, JFoundry also registers
the provider with every REST Client builder. Outbound logging uses
`jfoundry.web.rest-client.logging-level`, defaulting to `BASIC`. It does not add a REST Client
implementation by itself. Spring `WebClient` is not supported by this adapter.

All URIs exclude query, user-info, and fragment data. Sensitive headers and nested JSON fields are
redacted case-insensitively, and `FULL` retains at most 8 KiB. Client duration ends when response
headers arrive; response-body logging occurs after consumption or close. Jakarta REST exposes no
portable transport-failure callback, so the adapter does not use runtime-private hooks to claim
Spring-equivalent client failure logging.

## PostgreSQL Middleware Verification

The runtime-local JVM integration profile starts PostgreSQL through Testcontainers. It verifies that
the Quarkus `TransactionRunner`, JPA Outbox store, and Quarkus datasource wiring persist an Outbox
record in PostgreSQL rather than an in-memory test database. The profile is deliberately opt-in so a
regular module test does not require Docker:

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-quarkus/jfoundry-quarkus-integration-tests \
  -am -Pjvm-integration verify
```

## Native Image Verification

The repository's Quarkus native CI job installs the full reactor and then builds a separate consumer
application with Quarkus container Native Image build. Its `@QuarkusIntegrationTest` invokes
`TransactionRunner`, domain-event dispatch, Outbox dispatch, recovery, and cleanup through HTTP
endpoints against the native executable.

### CI-Aligned Local Verification

Run both Quarkus CI stages with Java 25 and Docker. On Linux, the native stage uses the same container
build as CI. On macOS, it uses local GraalVM because a Linux container executable cannot run on the
host:

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh quarkus
```

Use `--stage middleware` or `--stage native` to run one stage. To run all supported runtime checks,
use `bash scripts/verify-runtime-ci.sh all` with both environment variables set. The general
`scripts/verify-ci-matrix.sh` remains the Docker-free Java 25 baseline.

## Current Scope

This Quarkus integration covers CDI discovery, application transactions, RFC 9457 Problem Details,
HTTP server and MicroProfile REST Client diagnostic logging,
application-service domain-event dispatch, JPA aggregate persistence context assembly, optional JPA Outbox and
Inbox storage, automatic externalization for explicitly marked events, Kafka and RabbitMQ message delivery, and
optional Outbox dispatch, recovery, and cleanup. It does not yet provide Quarkus assembly for MyBatis-Plus,
RocketMQ, or starters. Those capabilities remain explicit follow-up work.
