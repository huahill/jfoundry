# Helidon MP Runtime Integration

`jfoundry-helidon` composes JFoundry's runtime-neutral contracts with Helidon MP 4.5.1. It is a
portable CDI/Jakarta runtime integration, not a Spring Boot starter and not a Quarkus extension.
Keep Helidon, CDI, JTA, JAX-RS, and Hibernate APIs outside domain and application code.

## Dependency Composition

Import the core JFoundry BOM, then the Helidon BOM for the same release line. The Helidon BOM manages
the selected Helidon platform ecosystem only; it does not manage JFoundry module versions:

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
            <artifactId>jfoundry-helidon-dependencies</artifactId>
            <version>${jfoundry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then select only the capabilities the application needs:

| Capability | JFoundry artifact | Application-provided Helidon capability |
|---|---|---|
| CDI transactions and local domain events | `jfoundry-helidon-runtime` | Helidon MP server and JTA CDI integration |
| JPA aggregate persistence | `jfoundry-persistence-jpa-helidon-runtime` | CDI JPA/Hibernate integration, datasource, and persistence unit |
| RFC 9457 JAX-RS responses | `jfoundry-web-helidon-runtime` | Helidon MP server |
| Outbox scheduling, dispatch, and automatic event externalization | `jfoundry-outbox-helidon-runtime` | an `OutboxMessageStore` and a real `MessageSender` |
| JPA Outbox store | `jfoundry-outbox-jpa-helidon-runtime` | JPA capability and application migration |
| JPA Inbox store | `jfoundry-inbox-jpa-helidon-runtime` | JPA capability and application migration |

The generic runtime does not implicitly add JPA, Outbox, Inbox, a database, or a broker client.

## Transactions And Domain Events

`jfoundry-helidon-runtime` exposes `TransactionRunner` through portable CDI and maps all six
`TransactionPropagation` modes to Jakarta Transactions. Timeout is supported for transactions it
creates. Transaction name and read-only options have no portable Jakarta Transactions equivalent and
are rejected rather than ignored.

The runtime also adds a CDI interceptor to JFoundry `@ApplicationService` beans. It collects domain
events registered through `DomainEventContext`, publishes them after the outermost successful
application-service invocation, and discards them when that invocation fails. The boundary is
synchronous; it does not support reactive return types.

## JPA, Outbox, And Inbox

The JPA aggregate capability supplies a transaction-bound aggregate persistence context and translates
recognized Hibernate connection and query-timeout failures to `ExternalAccessException`. Its
`EntityManager` is supplied by the Helidon application.

The JPA Outbox and Inbox capabilities reuse the framework-neutral JPA stores. They do not create SQL
tables: copy the published Outbox and Inbox SQL templates into the application's own migration process.
Inbox claim strategies support PostgreSQL and MySQL; another database requires an application
`JpaInboxClaimStrategy` bean.

`jfoundry-outbox-helidon-runtime` provides opt-in scheduling. Enable scheduled dispatch only after
providing both the store and broker sender:

```properties
jfoundry.outbox.dispatcher.enabled=true
```

The dispatcher properties match the runtime-neutral Outbox behavior: `interval` defaults to `5s`,
`batch-size` to `50`, `max-retries` to `5`, `backoff-base` to `1s`, and `backoff-max` to `5m`.

It also records domain events marked `@Externalized` into the current transaction when
`jfoundry.domain.event.dispatch.outbox.enabled=true`. The assembly provides Jackson serialization,
routing resolvers, an Outbox template, and a recorder as CDI alternatives at priority `1`. To replace
one of these defaults in a portable Helidon application, declare the replacement as an enabled CDI
`@Alternative` with a priority greater than `1`; a plain CDI bean does not override an enabled
alternative.

## Web Problems

`jfoundry-web-helidon-runtime` maps JFoundry application and domain exceptions to RFC 9457
`application/problem+json` JAX-RS responses. It keeps Helidon's ordinary handling for unknown
exceptions and unrelated HTTP failures; the adapter is not a replacement for the application's
general JAX-RS error policy.

## Native Image Status

The Helidon consumer is built with GraalVM Native Image and has verified CDI discovery, application
startup, and the Problem Details HTTP response. Use GraalVM 25 with Maven 3.9 and the repository's
Native Image profile:

```bash
GRAALVM_HOME=/path/to/graalvm-25 \
JAVA_HOME="$GRAALVM_HOME" PATH="$GRAALVM_HOME/bin:$PATH" \
mvn -pl jfoundry-runtime-integrations/jfoundry-helidon/integration-tests/jfoundry-helidon-integration-tests \
  -am -Pnative-image package
```

Helidon MP 4.5.1 documents Narayana JTA Native Image support as experimental. The native consumer
starts and serves Problem Details, but executing `TransactionRunner` currently fails because Helidon's
CDI transaction-manager delegate is not initialized in the generated image. JVM JTA remains supported.
JFoundry does not duplicate or replace Narayana to hide this upstream limitation, so Native JTA is not
an acceptance claim until Helidon provides a working supported path.

## Deferred Integrations

Helidon Kafka and RabbitMQ `MessageSender` adapters, Redisson distributed locking, and JobRunr are not
currently provided. Do not reuse Spring or Quarkus runtime adapters in a Helidon application. Add an
application-owned adapter only when its client lifecycle and delivery semantics are verified for the
selected Helidon release.
