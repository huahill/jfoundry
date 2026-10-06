package org.jfoundry.application.outbox;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JdkOutboxWorkerTest {

    @Test
    void dispatchesUntilClosed() throws Exception {
        CountDownLatch dispatched = new CountDownLatch(1);
        AtomicInteger dispatchCalls = new AtomicInteger();
        OutboxDispatcher dispatcher = batchSize -> {
            dispatchCalls.incrementAndGet();
            dispatched.countDown();
        };
        DefaultOutboxMaintenance maintenance = new DefaultOutboxMaintenance(new IdleStore(), new DirectTransactionRunner());
        JdkOutboxWorkerSettings settings = new JdkOutboxWorkerSettings(
                7,
                Duration.ofMillis(20),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                Duration.ofHours(24),
                7,
                30,
                1000);

        try (JdkOutboxWorker worker = JdkOutboxWorker.start(settings, dispatcher, maintenance)) {
            assertThat(dispatched.await(2, TimeUnit.SECONDS)).isTrue();
        }
        int afterClose = dispatchCalls.get();
        Thread.sleep(50);
        assertThat(dispatchCalls.get()).isEqualTo(afterClose);
    }

    @Test
    void settingsFromLookupOverrideDefaults() {
        JdkOutboxWorkerSettings.ValueLookup values = new JdkOutboxWorkerSettings.ValueLookup() {
            @Override
            public <T> java.util.Optional<T> get(String name, Class<T> type) {
                if (name.equals("jfoundry.outbox.dispatcher.batch-size") && type == Integer.class) {
                    return java.util.Optional.of(type.cast(12));
                }
                if (name.equals("jfoundry.outbox.dispatcher.enabled") && type == Boolean.class) {
                    return java.util.Optional.of(type.cast(false));
                }
                return java.util.Optional.empty();
            }
        };
        JdkOutboxWorkerSettings settings = JdkOutboxWorkerSettings.from(values);
        assertThat(settings.dispatchBatchSize()).isEqualTo(12);
        assertThat(settings.dispatchInterval()).isEqualTo(JdkOutboxWorkerSettings.defaults().dispatchInterval());
        assertThat(JdkOutboxWorkerSettings.workerEnabled(values)).isFalse();
    }


    private static final class DirectTransactionRunner implements TransactionRunner {
        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) {
            return callback.execute();
        }
    }

    private static final class IdleStore implements OutboxMessageStore {
        @Override
        public void append(OutboxMessage message) {
        }

        @Override
        public void markAsPublished(String eventId) {
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries,
                                 BackoffStrategy backoff) {
        }

        @Override
        public void reactivate(String eventId) {
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            return List.of();
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
            return 0;
        }
    }
}
