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

For cross-service tracing, propagate only the bounded W3C trace context carried by an
`OutboundMessage`; it remains separate from metrics and technical audit data.
