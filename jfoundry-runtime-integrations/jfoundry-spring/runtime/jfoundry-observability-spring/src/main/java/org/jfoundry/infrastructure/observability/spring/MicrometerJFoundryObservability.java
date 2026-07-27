package org.jfoundry.infrastructure.observability.spring;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jfoundry.application.inbox.InboxExecutionResult;
import org.jfoundry.application.inbox.InboxMessageProcessor;
import org.jfoundry.application.lock.LockCallback;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxRecorder;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/// Micrometer Observation decorators for framework operations in Spring applications.
/// <p>
/// The hosting application configures Observation handlers, including metrics and tracing bridges.
/// These decorators record only bounded operation and outcome tags.
public final class MicrometerJFoundryObservability {

    private static final String OPERATION_TAG = "jfoundry.operation";
    private static final String OUTCOME_TAG = "jfoundry.outcome";
    static final String OUTBOX_PERSIST = "jfoundry.outbox.persist";
    static final String OUTBOX_DISPATCH = "jfoundry.outbox.dispatch";
    static final String INBOX_PROCESS = "jfoundry.inbox.process";
    static final String LOCK_ACQUIRE = "jfoundry.lock.acquire";
    static final String SUCCESS = "success";
    static final String ERROR = "error";

    private final ObservationRegistry observationRegistry;

    public MicrometerJFoundryObservability(ObservationRegistry observationRegistry) {
        this.observationRegistry = Objects.requireNonNull(observationRegistry, "observationRegistry must not be null");
    }

    /// Returns an Outbox recorder that instruments append operations.
    public OutboxRecorder observe(OutboxRecorder delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new ObservedOutboxRecorder(delegate);
    }

    /// Returns an Outbox dispatcher that instruments dispatch runs.
    public OutboxDispatcher observe(OutboxDispatcher delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new ObservedOutboxDispatcher(delegate);
    }

    /// Returns an Inbox processor that instruments delivery processing.
    public InboxMessageProcessor observe(InboxMessageProcessor delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new ObservedInboxMessageProcessor(delegate);
    }

    /// Returns a lock executor that instruments lock acquisition and callback execution.
    public LockExecutor observe(LockExecutor delegate) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        return new ObservedLockExecutor(delegate);
    }

    <T, E extends Throwable> T observe(String operation, Function<T, String> outcome, CheckedSupplier<T, E> action)
            throws E {
        Observation observation = Observation.createNotStarted(operation, observationRegistry)
                .lowCardinalityKeyValue(OPERATION_TAG, operation)
                .start();
        try (Observation.Scope ignored = observation.openScope()) {
            T result = action.get();
            observation.lowCardinalityKeyValue(OUTCOME_TAG, outcome.apply(result));
            return result;
        } catch (Throwable exception) {
            observation.lowCardinalityKeyValue(OUTCOME_TAG, ERROR);
            return rethrow(exception);
        } finally {
            observation.stop();
        }
    }

    static String inboxOutcome(InboxExecutionResult result) {
        return result.name().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T rethrow(Throwable exception) throws E {
        throw (E) exception;
    }

    @FunctionalInterface
    interface CheckedSupplier<T, E extends Throwable> {

        T get() throws E;
    }

    private final class ObservedOutboxRecorder implements OutboxRecorder, MicrometerObservedOperation {

        private final OutboxRecorder delegate;

        private ObservedOutboxRecorder(OutboxRecorder delegate) {
            this.delegate = delegate;
        }

        @Override
        public void append(org.jfoundry.application.outbox.OutboxAppendRequest request) {
            observe(OUTBOX_PERSIST, ignored -> SUCCESS, () -> {
                delegate.append(request);
                return null;
            });
        }
    }

    private final class ObservedOutboxDispatcher implements OutboxDispatcher, MicrometerObservedOperation {

        private final OutboxDispatcher delegate;

        private ObservedOutboxDispatcher(OutboxDispatcher delegate) {
            this.delegate = delegate;
        }

        @Override
        public void dispatch(int batchSize) {
            observe(OUTBOX_DISPATCH, ignored -> SUCCESS, () -> {
                delegate.dispatch(batchSize);
                return null;
            });
        }
    }

    private final class ObservedInboxMessageProcessor implements InboxMessageProcessor, MicrometerObservedOperation {

        private final InboxMessageProcessor delegate;

        private ObservedInboxMessageProcessor(InboxMessageProcessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public InboxExecutionResult executeOnce(String messageId, String consumerName,
                                                org.jfoundry.application.inbox.InboxHandler handler) {
            return observe(INBOX_PROCESS, MicrometerJFoundryObservability::inboxOutcome,
                    () -> delegate.executeOnce(messageId, consumerName, handler));
        }
    }

    private final class ObservedLockExecutor implements LockExecutor, MicrometerObservedOperation {

        private final LockExecutor delegate;

        private ObservedLockExecutor(LockExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T execute(LockKey key, LockOptions options, LockCallback<T> callback) throws Exception {
            return observe(LOCK_ACQUIRE, ignored -> SUCCESS, () -> delegate.execute(key, options, callback));
        }
    }
}
