package org.jfoundry.infrastructure.observability.spring;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerJFoundryObservabilityTest {

    @Test
    void recordsBoundedSignalsWithoutBusinessIdentifiers() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        CompletedObservations completed = new CompletedObservations();
        registry.observationConfig().observationHandler(completed);
        MicrometerJFoundryObservability observations = new MicrometerJFoundryObservability(registry);

        observations.observe((OutboxRecorder) request -> {}).append(OutboxAppendRequest.of(
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
        }).execute(new LockKey("order-confirmation", "lock-secret"), LockOptions.defaults(), () -> null);

        assertThat(completed.contexts).extracting(Observation.Context::getName)
                .containsExactlyInAnyOrder("jfoundry.outbox.persist", "jfoundry.outbox.dispatch",
                        "jfoundry.inbox.process", "jfoundry.lock.acquire");
        String signals = completed.contexts.toString();
        assertThat(signals).contains("jfoundry.operation", "jfoundry.outcome", "success", "processed")
                .doesNotContain("event-secret", "orders.secret", "partition-secret", "message-secret",
                        "consumer-secret", "lock-secret");
    }

    @Test
    void recordsBoundedErrorOutcomeWithoutAttachingTheException() {
        ObservationRegistry registry = ObservationRegistry.create();
        CompletedObservations completed = new CompletedObservations();
        registry.observationConfig().observationHandler(completed);
        MicrometerJFoundryObservability observations = new MicrometerJFoundryObservability(registry);

        OutboxRecorder recorder = request -> {
            throw new IllegalStateException("database password=secret");
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> observations.observe(recorder)
                .append(OutboxAppendRequest.of(
                        "event-secret", "orders.secret", null, "order-confirmed", new Object(), Instant.EPOCH)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database password=secret");

        assertThat(completed.contexts).singleElement().satisfies(context -> {
            assertThat(context.getLowCardinalityKeyValue("jfoundry.outcome").getValue()).isEqualTo("error");
            assertThat(context.getError()).isNull();
        });
        assertThat(completed.contexts.toString()).doesNotContain("database password=secret", "event-secret", "orders.secret");
    }

    private static final class CompletedObservations implements ObservationHandler<Observation.Context> {

        private final List<Observation.Context> contexts = new ArrayList<>();

        @Override
        public void onStop(Observation.Context context) {
            contexts.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
