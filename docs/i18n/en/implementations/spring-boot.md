# Spring Boot Runtime Assembly

Spring Boot is a peer runtime integration for the framework-neutral jfoundry core. It uses Spring
Boot starters and conditional auto-configuration to assemble selected capabilities. It does not
make Spring APIs part of the domain or application model, and it does not imply that every
capability is enabled by the base starter.

## Assembly Model

Import `jfoundry-dependencies` for JFoundry module versions and
`jfoundry-spring-dependencies` for the Spring platform, then add
`jfoundry-spring-boot-starter` in the runtime assembly module. The base starter intentionally
remains small: it provides general Boot wiring and a Spring-backed `TransactionRunner`, but no
persistence provider, broker, Outbox, Inbox, JobRunr, or Redisson client.

The Spring runtime BOM manages the aligned Spring Boot, Spring Cloud, and Spring Cloud Alibaba BOMs.
It manages their versions only: applications still declare the selected Cloud starters explicitly,
and that management alone does not create a JFoundry adapter for each Cloud capability.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Add every other capability explicitly. This keeps a Spring Boot application honest about its
database, delivery, scheduling, and distributed-lock choices.

## Capability Composition

| Need | Add | Boundary |
|---|---|---|
| Local application transactions | `jfoundry-spring-boot-starter` | Supplies the Spring `TransactionRunner`; applications may replace it. |
| Local domain-event listeners | `jfoundry-event-spring-boot-starter` | Publishes domain events through Spring application events; it is not an Outbox or broker. |
| Aggregate persistence with MyBatis-Plus | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | Business aggregate persistence only; no Outbox or Inbox store. |
| Aggregate persistence with JPA | `jfoundry-persistence-jpa-spring-boot-starter` | One managed entity graph per aggregate; no Outbox or Inbox store. |
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
not dispatch their pending aggregate events.

## Persistence

The persistence starters are named after the capability they assemble, not merely the ORM they
pull in. `jfoundry-persistence-jpa-spring-boot-starter` assembles the JPA aggregate adapter,
Spring transaction-bound persistence context, and Spring Boot JPA runtime. Its MyBatis-Plus peer
does the same for MyBatis-Plus business aggregate persistence.

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
one, no production delivery path exists. Automatic domain-event externalization is disabled by
default and should only be enabled when the annotated domain event is intentionally a stable
integration contract. See [reliable messaging](../capabilities/reliable-messaging.md).

## Web, Locks, And Replacement

The Web MVC starter is an inbound adapter. It renders the shared RFC 9457 contract for supported
jfoundry exceptions and cooperates with Spring MVC's own HTTP error handling; domain and application
code must not select HTTP statuses directly.

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
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pit verify
```

The same module also contains a minimal AOT consumer. On GraalVM Native Image, the `native` profile
builds it and CI starts the executable, then verifies `GET /jfoundry/native/ready` returns `ready`:

```bash
./mvnw -B \
  -pl jfoundry-runtime-integrations/jfoundry-spring/jfoundry-spring-integration-tests \
  -am -Pnative package
```

### CI-Aligned Local Verification

Run both Spring CI stages locally with Java 25, Docker, and GraalVM Native Image:

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh spring
```

Use `--stage middleware` or `--stage native` to run one stage. The general
`scripts/verify-ci-matrix.sh` remains the Docker-free Java 25 baseline.
