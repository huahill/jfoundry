# Observability

Observability is an optional outer adapter. JFoundry application contracts do not create SDKs,
exporters, collectors, samplers, or resource attributes. The host application owns that lifecycle
and selects its telemetry backend.

`jfoundry-observability-otel` provides OpenTelemetry API decorators for four framework operations:

- `jfoundry.outbox.persist`
- `jfoundry.outbox.dispatch`
- `jfoundry.inbox.process`
- `jfoundry.lock.acquire`

The decorator records a span and the `jfoundry.operation.count` counter for each invocation. Its
only attributes are the fixed operation name and a bounded outcome. It never records message IDs,
aggregate IDs, topics, payload keys, consumer names, lock values, or exception text.

```java
OpenTelemetryJFoundryObservability observations =
        new OpenTelemetryJFoundryObservability(openTelemetry);

OutboxRecorder recorder = observations.observe(new OutboxTemplate(store, serializer));
LockExecutor lockExecutor = observations.observe(LockExecutor.create(lockClient));
```

Compose exactly one instrumentation implementation around an operation. In particular, do not
wrap a Spring Micrometer-observed operation with this direct OpenTelemetry decorator, because that
would duplicate spans and metrics.

## Spring Boot

Spring Boot applications can add `jfoundry-observability-spring-boot-starter`. It supplies the
Micrometer Observation API, Spring AOP, and the Actuator runtime, then auto-configures observation
of eligible `OutboxRecorder`, `OutboxDispatcher`, `InboxMessageProcessor`, and `LockExecutor` beans.
The operation names and bounded outcome tags are identical to the direct OpenTelemetry adapter.

Micrometer Observation is the only JFoundry instrumentation path for those Spring beans. Configure
metrics registries and an OpenTelemetry tracing bridge through the host application's normal Spring
Boot observability configuration. Do not add `jfoundry-observability-otel` around a Spring bean
already observed by this starter.

The runtime also recognizes an operation already wrapped by `MicrometerJFoundryObservability` and
does not apply its Spring advisor again. Choose either the automatic Spring integration or a manual
Micrometer wrapper as the application composition style.

For cross-service tracing, propagate only the bounded W3C trace context carried by an
`OutboundMessage`; it remains separate from metrics and technical audit data.
