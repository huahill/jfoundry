# Helidon MP Runtime Integration

`jfoundry-helidon` composes JFoundry's runtime-neutral contracts with the supported Helidon MP version. It is a
portable CDI/Jakarta runtime integration, not a Spring Boot starter and not a Quarkus extension.
Keep Helidon, CDI, JTA, JAX-RS, and Hibernate APIs outside domain and application code.

Its transaction, JTA domain-event coordination, and JAX-RS HTTP logging reuse the portable
`jfoundry-transaction-jta`, `jfoundry-domain-event-jta`, and `jfoundry-web-jaxrs` implementations.
Helidon-owned runtime classes remain the public CDI/provider entry points and retain portable-extension,
service-loading, scheduling, logging, and Native Image behavior. Applications select the Helidon runtime
modules rather than assembling these shared implementation modules.

See the [compatibility matrix](../../../release/compatibility.md) for the exact platform version.

## Dependency Composition

Import the Helidon BOM before the core JFoundry BOM, using the same JFoundry version for both. The Helidon BOM manages
the selected Helidon platform ecosystem and its narrow Jackson annotations compatibility alignment;
it does not manage JFoundry module versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-helidon-dependencies</artifactId>
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

Then select only the capabilities the application needs:

| Capability | JFoundry artifact | Application-provided Helidon capability |
|---|---|---|
| CDI transactions and local domain events | `jfoundry-helidon-runtime` | Helidon MP server and JTA CDI integration |
| JPA aggregate persistence | `jfoundry-persistence-jpa-helidon-runtime` | CDI JPA/Hibernate integration, datasource, and persistence unit |
| RFC 9457 JAX-RS responses | `jfoundry-web-helidon-runtime` | Helidon MP server; Bean Validation for request-validation mapping |
| Outbox scheduling, dispatch, and automatic event externalization | `jfoundry-outbox-helidon-runtime` | an `OutboxMessageStore` and a real `MessageSender` |
| JPA Outbox store | `jfoundry-outbox-jpa-helidon-runtime` | JPA capability and application migration |
| JPA Inbox store | `jfoundry-inbox-jpa-helidon-runtime` | JPA capability and application migration |

The generic runtime does not implicitly add JPA, Outbox, Inbox, a database, or a broker client.

## Transactions And Domain Events

`jfoundry-helidon-runtime` exposes `TransactionRunner` through portable CDI and maps all six
`TransactionPropagation` modes to Jakarta Transactions. Timeout is supported for transactions it
creates. Transaction name and read-only options have no portable Jakarta Transactions equivalent and
are rejected rather than ignored.

The runtime also adds a CDI interceptor to JFoundry `@ApplicationService` beans. For events
registered in an active JTA transaction, it records the Outbox path in `beforeCompletion` and notifies
ordinary CDI dispatchers only after a successful commit. Outside a transaction, it dispatches after
the outermost successful application-service invocation and discards events when that invocation
fails. The boundary is synchronous; it does not support reactive return types.

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

## Web Integration

`jfoundry-web-helidon-runtime` maps JFoundry application and domain exceptions to RFC 9457
`application/problem+json` JAX-RS responses. It keeps Helidon's ordinary handling for unknown
exceptions and unrelated HTTP failures; the adapter is not a replacement for the application's
general JAX-RS error policy. The runtime-neutral contract and the dependency choices for all
supported runtimes are in [Web](../capabilities/web.md).

It does not configure security. A Helidon security adapter that owns authentication and authorization
can render its own `401` or `403` descriptor with `ProblemDetailsRenderer.render(...)`. Extension
values preserve JSON scalar, array, and object types across the runtime adapters.

For request validation, add `helidon-microprofile-bean-validation`. The JFoundry mapper converts only
constraint violations rooted in a JAX-RS resource input into
`urn:jfoundry:problem:request-validation`; it never accesses or returns rejected values. Return-value
violations and validation failures from internal CDI services are rethrown so Helidon retains its
server-error handling. Applications that accept JSON request bodies must also select a Jersey JSON
provider, such as `jersey-media-json-binding` for JSON-B.

The same runtime module registers a JAX-RS request/response filter and reader/writer interceptors for
diagnostic logging. Inbound logging uses `jfoundry.web.helidon.logging-level`, defaulting to `NONE`.
Enable `BASIC`, `HEADERS`, or `FULL` together with `DEBUG` for
`org.jfoundry.http.helidon.HttpLoggingProvider`.

When the application selects `helidon-microprofile-rest-client`, JFoundry automatically registers its
provider with every MicroProfile REST Client builder. Outbound logging uses
`jfoundry.web.rest-client.logging-level`, defaulting to `BASIC`; the Web runtime does not add the
client implementation itself. This integration is verified on the JVM. Helidon 4.5.3's REST Client
Native Image substitution is not compatible with the current GraalVM 25 baseline, so Native REST
Client logging is not a release support claim. Spring `WebClient` is not supported.

URI queries, user information, and fragments are never logged. Sensitive headers and nested JSON
fields are redacted case-insensitively, and body capture is capped at 8 KiB. Client duration ends when
response headers arrive, while body logs appear after consumption or close. Jakarta REST has no
portable transport-failure callback, so this adapter does not depend on Helidon-private hooks to
claim Spring-equivalent failure logging.

## PostgreSQL/JTA Middleware Verification

The runtime-local JVM integration profile starts PostgreSQL through Testcontainers and verifies a
real JTA `TransactionRunner` callback with a JPA `EntityManager`. Helidon's CDI JPA integration
uses the standard `META-INF/persistence.xml` persistence-unit descriptor and resolves its named JTA
datasource through CDI; this is the same integration model the verification exercises. The profile
is opt-in so ordinary module tests do not require Docker:

```bash
./mvnw -B \
  -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests \
  -am -Pjvm-integration verify
```

## Native Image Status

The Helidon consumer is built with GraalVM Native Image and has verified CDI discovery, application
startup, and the Problem Details HTTP response. Use GraalVM 25 with Maven 3.9 and the repository's
Native Image profile:

```bash
GRAALVM_HOME=/path/to/graalvm-25 \
JAVA_HOME="$GRAALVM_HOME" PATH="$GRAALVM_HOME/bin:$PATH" \
mvn -pl jfoundry-runtime/jfoundry-helidon/jfoundry-helidon-integration-tests \
  -am -Pnative-image package
```

The Native consumer also verifies JSON-B request deserialization, Bean Validation, and the request-
validation Problem Details response. Because Helidon embeds CDI metadata into the image, the profile
initializes the validation provider, EL implementation, and ClassMate metadata at build time and
registers its request DTO fields for reflection. Downstream Native applications must provide
equivalent reflection metadata for their own JSON and validated request types.

The supported Helidon MP version documents Narayana JTA Native Image support as experimental. In the
tested GraalVM Community environment on macOS ARM64, the JPA-enabled consumer fails during image generation because
`org.xml.sax.helpers.LocatorImpl` reaches the image heap through
`JpaExtension.processPersistenceXmls`. The Native CDI/Web-only consumer starts and serves Problem
Details, but executing `TransactionRunner` currently fails because Helidon's CDI transaction-manager
delegate is not initialized in the generated image. JVM JTA remains supported. The reproducible
environment, JVM control result, and Native failure trace are recorded on
[Helidon issue #8863](https://github.com/helidon-io/helidon/issues/8863#issuecomment-5078931015).
JFoundry does not duplicate or replace Narayana to hide this upstream limitation, so Native JTA and
JPA are not acceptance claims until Helidon provides working supported paths. Exact tested versions
are recorded in the [compatibility matrix](../../../release/compatibility.md).

### CI-Aligned Local Verification

Run both Helidon CI stages locally with Java 25, Docker, and GraalVM Native Image:

```bash
JAVA_25_HOME=/path/to/java-25 \
GRAALVM_HOME=/path/to/graalvm-25 \
bash scripts/verify-runtime-ci.sh helidon
```

Use `--stage middleware` or `--stage native` to run one stage. The native stage verifies the
supported CDI/Web consumer, ordinary Problem Details response, and request-validation response; it
does not claim Native JTA or JPA support. The general `scripts/verify-ci-matrix.sh` remains the
Docker-free Java 25 baseline.

## Deferred Integrations

Helidon Kafka and RabbitMQ `MessageSender` adapters, Redisson distributed locking, and JobRunr are not
currently provided. Do not reuse Spring or Quarkus runtime adapters in a Helidon application. Add an
application-owned adapter only when its client lifecycle and delivery semantics are verified for the
selected Helidon release.
