# Spring Boot Runtime Assembly

Spring Boot is a peer runtime integration for the framework-neutral jfoundry core. It uses Spring
Boot starters and conditional auto-configuration to assemble selected capabilities. It does not
make Spring APIs part of the domain or application model, and it does not imply that every
capability is enabled by the base starter.

## Common Prerequisite

Every external application must have `jfoundry-dependencies` in dependency management. It manages
versions for JFoundry core, architecture, and framework-neutral adapter modules. The JFoundry Boot
parent imports it automatically; other applications must import it explicitly. It belongs in
`<dependencyManagement>` and is not a runtime dependency. A Spring runtime BOM manages Spring
platform versions only; it does not replace the core JFoundry BOM.

## Spring Boot

A Spring Boot application that does not use Spring Cloud should use `jfoundry-spring-boot-parent` as
its only Maven parent. The parent
inherits the supported Spring Boot parent, sets Java 25, and already imports
`jfoundry-spring-boot-dependencies` followed by `jfoundry-dependencies`; applications using this
parent must not import those two BOMs again.

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>${jfoundry.version}</version>
</parent>
```

`jfoundry-spring-boot-starter` is only the minimal shared baseline for Spring Boot capability starters.
It does not provide transactions, persistence, messaging, or Outbox. Usually select the capability
starter the application needs; that starter brings the shared baseline when required. Do not treat the
base starter as the transaction starter.

## Spring Cloud

An application that needs Spring Cloud or Spring Cloud Alibaba must not use the JFoundry Boot parent.
Use an application or standard Maven parent compatible with the supported Spring Cloud versions, then import
`jfoundry-spring-cloud-dependencies` before `jfoundry-dependencies`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-cloud-dependencies</artifactId>
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
```

The Cloud BOM manages Spring Cloud and Spring Cloud Alibaba; Spring Boot remains managed by the
application parent or another explicit Boot BOM. Do not import both
`jfoundry-spring-boot-dependencies` and `jfoundry-spring-cloud-dependencies`. The Cloud BOM manages
the platform ecosystem only; it does not add Cloud starters or imply that JFoundry provides an
adapter for every Cloud component.

An application retaining another parent while using Spring Boot without Spring Cloud should import
`jfoundry-spring-boot-dependencies` before `jfoundry-dependencies` as described in
[Getting Started](../integration/getting-started.md), then manage Java and Spring Boot versions itself.

Add every other capability explicitly. This keeps a Spring Boot application honest about its
database, delivery, scheduling, and distributed-lock choices.

## Capability Composition

| Need | Add | Boundary |
|---|---|---|
| Local application transactions | `jfoundry-transaction-spring-boot-starter` | Supplies Spring `TransactionRunner` integration; Spring Boot or the application provides the transaction manager. |
| Local domain-event listeners | `jfoundry-domain-event-spring-boot-starter` | Publishes domain events through Spring application events; it is not an Outbox or broker. |
| Aggregate persistence with JPA | `jfoundry-persistence-jpa-spring-boot-starter` | One managed entity graph per aggregate; no Outbox or Inbox store. |
| Aggregate persistence with MyBatis-Plus | `jfoundry-persistence-mybatis-plus-spring-boot-starter` | Business aggregate persistence only; no Outbox or Inbox store. |
| RFC 9457 Web MVC errors | `jfoundry-webmvc-spring-boot-starter` | HTTP inbound adapter only. |
| Outbound `RestClient` support and configurable HTTP logging | `jfoundry-restclient-spring-boot-starter` | Applies to Spring Boot-managed `RestClient.Builder` instances; manual builders use the Java API. |
| JSON serialization contract | `jfoundry-messaging-spring-boot-starter` | Adds Spring messaging integration and the default Jackson `PayloadSerializer`, but no real sender. |
| Kafka, RabbitMQ, or RocketMQ delivery | Matching `jfoundry-messaging-*-spring-boot-starter` | Select a concrete broker transport explicitly. |
| Outbox capability | `jfoundry-outbox-spring-boot-starter` | Adds recording, externalization, recovery, cleanup, and the built-in scheduled dispatch trigger. Add it directly for manual composition; built-in store and JobRunr starters include it transitively. |
| Outbox store | `jfoundry-outbox-jpa-spring-boot-starter`, `jfoundry-outbox-mybatis-plus-spring-boot-starter`, or an application `OutboxMessageStore` | Persists Outbox records only; built-in store starters also bring the Outbox capability, while applications still own migrations. |
| Inbox runtime and store | `jfoundry-inbox-spring-boot-starter` plus one `jfoundry-inbox-*-spring-boot-starter` | Consumer idempotency; applications own migrations. |
| Outbox dispatch trigger | Built-in scheduled mode, optional `jfoundry-outbox-jobrunr-spring-boot-starter`, or an application dispatcher | JobRunr replaces the built-in trigger and brings the Outbox capability transitively; every option still needs an Outbox store and real sender. |
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
starter only after the use case requires durable external publication or consumer idempotency;
normally use the same persistence technology for business data and the reliable messaging store.
Aggregate mapping, optimistic-locking, and repository-shape decisions remain in the
[JPA](jpa.md) and [MyBatis-Plus](mybatis-plus.md) implementation guides.

## Reliable Messaging

Outbox assembly has four independent decisions: capability, store, dispatch trigger, and message
transport. The shared `outbox` prefix indicates which capability an adapter serves; it does not make
JPA, MyBatis-Plus, or JobRunr a complete Outbox solution. JFoundry does not create database tables or
invent message destinations. Copy the selected SQL template into the application's own migration
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

For a catalog-supported Spring MVC client error, JFoundry keeps the catalog's stable `type` and
`status` while using the exception-specific `title` and `detail` produced by Spring Framework,
including `MessageSource` localization. For example, a missing request parameter identifies that
parameter, an unsupported method identifies the method, and an unreadable request body reports that
the request could not be read. Catalog text remains the fallback when Spring provides no specific
problem body. Server-side failures continue to use reviewed catalog text rather than exception
messages, causes, or other diagnostic details. Type-conversion failures identify the affected
request property when available but do not echo its rejected value.

Spring MVC request-input validation failures use the dedicated
`urn:jfoundry:problem:request-validation` type. Its `errors` extension follows the RFC 9457
validation-error example: each entry has a human-readable `detail` and, when the error can be proven
to belong to a JSON body field, a `pointer` expressed as a JSON Pointer URI fragment. Query, path,
header, cookie, matrix, model-attribute, and multipart errors contain only `detail`. Object-level and
cross-parameter constraints also contain only `detail` because they have no reliable JSON location:

```json
{
  "type": "urn:jfoundry:problem:request-validation",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request failed validation. See 'errors' for details.",
  "errors": [
    {
      "detail": "must not be empty",
      "pointer": "#/services"
    }
  ]
}
```

Rejected values are never included because request fields may contain credentials, tokens, or large
payloads. Spring MVC derives this shared contract from `MethodArgumentNotValidException` and
`HandlerMethodValidationException`; return-value validation remains a server-side failure. Quarkus
and Helidon derive the same external representation from their runtime-specific request-validation
exceptions, as described in their implementation guides.

`jfoundry-restclient-spring` is the opt-in Spring integration for outbound `RestClient` calls. Configure
only the builder that owns the integration with `RestClientSupport.configure(builder)`, then execute
the selected call through `RestClientSupport.execute(...)`. A non-success response becomes an
`HttpResponseException` containing only its status code. Transport and response-decoding failures
become an `HttpRequestException` with a safe failure kind. Import `HttpLoggingLevel` from
`org.jfoundry.http`, Spring's logging support from `org.jfoundry.http.spring`, the execution-chain interceptor from `org.jfoundry.http.spring.client`, and
the `RestClient` APIs from `org.jfoundry.web.spring.client`. The former `org.jfoundry.web.spring`
locations have no forwarding aliases.

Spring Boot applications can use `jfoundry-restclient-spring-boot-starter` and set
`jfoundry.web.rest-client.logging-level` to `NONE`, `BASIC`, `HEADERS`, or `FULL`; its default is
`NONE`. Applications that create a builder directly with `RestClient.builder()` must select the level
with `RestClientSupport.configure(builder, HttpLoggingLevel)`. The outbound `duration` field uses an `ms` suffix,
such as `duration=30ms`, and a monotonic clock from execution-chain entry until response headers arrive or execution
fails. Response-body
consumption and decoding occur outside that boundary.

`jfoundry-webmvc-spring-boot-starter` auto-configures `HttpLoggingFilter` for Servlet applications.
`jfoundry.web.mvc.logging-level` defaults to `NONE`, so upgrades do not silently increase access-log
volume. Enabled registration covers `REQUEST`, `ASYNC`, and `ERROR`, supports async processing, and
defaults to `Ordered.HIGHEST_PRECEDENCE + 20`, before Spring Security's normal registration. An
application-provided `HttpLoggingFilter` or `FilterRegistrationBean<HttpLoggingFilter>` replaces this
default when forwarding, tracing, or security topology requires another order.

The auto-configured filter excludes `/actuator/health/**` by default, including liveness and readiness
probes. Set `jfoundry.web.mvc.logging-excluded-paths` to a list of Ant-style application paths to replace
the default list; include `/actuator/health/**` in that list when adding exclusions while retaining the
health exclusion. Matching removes the Servlet context path before evaluating a pattern.

Inbound duration ends when the synchronous chain completes or the async request reaches terminal
complete, error, or timeout. Tee wrappers forward request and response bytes immediately and retain at
most 8 KiB for `FULL`; this does not measure when the client receives a streamed response. Both
directions emit categorized request, headers, body, and response events at `INFO`, always remove URI queries,
redact sensitive headers and nested JSON fields, and omit unsafe body representations. These logs supplement rather than replace Micrometer
metrics/traces and application-owned business audit events.

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

The same module also contains a minimal AOT consumer for the supported Spring Boot version. On GraalVM Native Image, the
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
claim applies to the supported Spring Boot and MyBatis-Plus versions with PostgreSQL; it does not certify
JPA, brokers, Redisson, or JobRunr. Exact tested versions are recorded in the
[compatibility matrix](../../../release/compatibility.md). It also verifies the built-in MyBatis-Plus Outbox and Inbox
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
