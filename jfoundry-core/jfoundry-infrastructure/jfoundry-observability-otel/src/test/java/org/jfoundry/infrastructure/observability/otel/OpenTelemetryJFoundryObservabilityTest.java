package org.jfoundry.infrastructure.observability.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.jfoundry.application.inbox.InboxExecutionResult;
import org.jfoundry.application.inbox.InboxMessageProcessor;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.jfoundry.application.outbox.OutboxAppendRequest;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryJFoundryObservabilityTest {

    @Test
    void recordsBoundedSignalsWithoutBusinessIdentifiers() throws Exception {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        OpenTelemetry telemetry = telemetry(spanExporter, metricReader);
        OpenTelemetryJFoundryObservability observations = new OpenTelemetryJFoundryObservability(telemetry);

        OutboxRecorder recorder = request -> {};
        observations.observe(recorder).append(OutboxAppendRequest.of(
                "event-secret", "orders.secret", "partition-secret", "order-confirmed", new Object(), Instant.EPOCH));
        observations.observe((OutboxDispatcher) batchSize -> {}).dispatch(10);
        observations.observe((InboxMessageProcessor) (messageId, consumer, handler) -> {
            handler.handle();
            return InboxExecutionResult.PROCESSED;
        }).executeOnce("message-secret", "consumer-secret", () -> {});
        observations.observe(new LockExecutor() {
            @Override
            public <T> T execute(LockKey key, LockOptions options,
                                 org.jfoundry.application.lock.LockCallback<T> callback) throws Exception {
                return callback.execute();
            }
        })
                .execute(new LockKey("order-confirmation", "lock-secret"), LockOptions.defaults(), () -> null);

        assertThat(spanExporter.getFinishedSpanItems())
                .extracting(span -> span.getName())
                .containsExactlyInAnyOrder("jfoundry.outbox.persist", "jfoundry.outbox.dispatch",
                        "jfoundry.inbox.process", "jfoundry.lock.acquire");
        assertThat(metricReader.collectAllMetrics())
                .allSatisfy(metric -> assertThat(metric.getName()).isEqualTo("jfoundry.operation.count"));

        String serializedSignals = spanExporter.getFinishedSpanItems().toString()
                + metricReader.collectAllMetrics();
        assertThat(serializedSignals)
                .doesNotContain("event-secret", "orders.secret", "partition-secret", "message-secret",
                        "consumer-secret", "lock-secret");
    }

    @Test
    void recordsOnlyBoundedErrorOutcomeWhenDelegateFails() {
        InMemorySpanExporter spanExporter = InMemorySpanExporter.create();
        InMemoryMetricReader metricReader = InMemoryMetricReader.create();
        OpenTelemetryJFoundryObservability observations = new OpenTelemetryJFoundryObservability(
                telemetry(spanExporter, metricReader));

        OutboxRecorder recorder = request -> {
            throw new IllegalStateException("database password=secret");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> observations.observe(recorder)
                .append(OutboxAppendRequest.of(
                        "event-secret", "orders.secret", null, "order-confirmed", new Object(), Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database password=secret");

        String serializedSignals = spanExporter.getFinishedSpanItems().toString()
                + metricReader.collectAllMetrics();
        assertThat(serializedSignals)
                .contains("jfoundry.outbox.persist", "error")
                .doesNotContain("database password=secret", "event-secret", "orders.secret");
    }

    private static OpenTelemetry telemetry(InMemorySpanExporter spanExporter, InMemoryMetricReader metricReader) {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .build();
    }
}
