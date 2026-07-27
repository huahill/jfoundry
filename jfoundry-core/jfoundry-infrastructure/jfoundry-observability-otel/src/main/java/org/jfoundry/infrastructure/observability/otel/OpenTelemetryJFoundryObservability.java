package org.jfoundry.infrastructure.observability.otel;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.jfoundry.application.inbox.InboxMessageProcessor;
import org.jfoundry.application.lock.LockCallback;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxRecorder;

import java.util.Objects;
import java.util.function.Function;

/// Optional OpenTelemetry API decorators for framework operations.
/// <p>
/// The hosting application owns SDK lifecycle, exporters, sampling, and resource attributes. These
/// decorators record only bounded operation and outcome attributes.
public final class OpenTelemetryJFoundryObservability {

    private static final String INSTRUMENTATION_SCOPE = "org.jfoundry.observability";
    private static final String OPERATION_ATTRIBUTE = "jfoundry.operation";
    private static final String OUTCOME_ATTRIBUTE = "jfoundry.outcome";
    private static final String OUTBOX_PERSIST = "jfoundry.outbox.persist";
    private static final String OUTBOX_DISPATCH = "jfoundry.outbox.dispatch";
    private static final String INBOX_PROCESS = "jfoundry.inbox.process";
    private static final String LOCK_ACQUIRE = "jfoundry.lock.acquire";
    private static final String SUCCESS = "success";
    private static final String ERROR = "error";

    private final Tracer tracer;
    private final LongCounter operationCounter;

    public OpenTelemetryJFoundryObservability(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
        this.operationCounter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE)
                .counterBuilder("jfoundry.operation.count")
                .setDescription("Framework operation outcomes")
                .build();
    }

    /// Returns an Outbox recorder that instruments append operations.
    public OutboxRecorder observe(OutboxRecorder delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return request -> observeUnchecked(OUTBOX_PERSIST, ignored -> SUCCESS, () -> {
            delegate.append(request);
            return null;
        });
    }

    /// Returns an Outbox dispatcher that instruments dispatch runs.
    public OutboxDispatcher observe(OutboxDispatcher delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return batchSize -> observeUnchecked(OUTBOX_DISPATCH, ignored -> SUCCESS, () -> {
            delegate.dispatch(batchSize);
            return null;
        });
    }

    /// Returns an Inbox processor that instruments delivery processing.
    public InboxMessageProcessor observe(InboxMessageProcessor delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return (messageId, consumerName, handler) -> observeUnchecked(INBOX_PROCESS,
                result -> result.name().toLowerCase(),
                () -> delegate.executeOnce(messageId, consumerName, handler));
    }

    /// Returns a lock executor that instruments lock acquisition and callback execution.
    public LockExecutor observe(LockExecutor delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new LockExecutor() {
            @Override
            public <T> T execute(LockKey key, LockOptions options, LockCallback<T> callback) throws Exception {
                return observeChecked(LOCK_ACQUIRE, ignored -> SUCCESS,
                        () -> delegate.execute(key, options, callback));
            }
        };
    }

    private <T> T observeUnchecked(String operation, Function<T, String> outcome, CheckedSupplier<T> action) {
        try {
            return observeChecked(operation, outcome, action);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Observation delegate failed", exception);
        }
    }

    private <T> T observeChecked(String operation, Function<T, String> outcome, CheckedSupplier<T> action)
            throws Exception {
        Span span = tracer.spanBuilder(operation).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T result = action.get();
            record(operation, outcome.apply(result));
            return result;
        } catch (Exception exception) {
            span.setStatus(StatusCode.ERROR);
            record(operation, ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    private void record(String operation, String outcome) {
        Attributes attributes = Attributes.builder()
                .put(OPERATION_ATTRIBUTE, operation)
                .put(OUTCOME_ATTRIBUTE, outcome)
                .build();
        Span.current().setAllAttributes(attributes);
        operationCounter.add(1, attributes);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {

        T get() throws Exception;
    }
}
