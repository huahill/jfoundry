package org.jfoundry.application.outbox;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultOutboxMaintenanceTest {

    @Test
    void recoverStuckDispatchingUsesCutoffAndReturnsStoreCount() {
        RecordingStore store = new RecordingStore();
        store.recoverResult = 3;
        CountingTransactionRunner transactionRunner = new CountingTransactionRunner();
        DefaultOutboxMaintenance maintenance = new DefaultOutboxMaintenance(store, transactionRunner);

        int recovered = maintenance.recoverStuckDispatching(Duration.ofMinutes(5));

        assertThat(recovered).isEqualTo(3);
        assertThat(store.recoverCutoff).isNotNull();
        assertThat(store.recoverCutoff).isBeforeOrEqualTo(Instant.now().minus(Duration.ofMinutes(4)));
        assertThat(transactionRunner.options).singleElement().satisfies(options -> {
            assertThat(options.name()).isEmpty();
            assertThat(options.propagation()).isEqualTo(TransactionPropagation.REQUIRES_NEW);
        });
    }

    @Test
    void recoverStuckDispatchingReturnsZeroWhenStoreIsMissing() {
        DefaultOutboxMaintenance maintenance = new DefaultOutboxMaintenance(() -> null, new CountingTransactionRunner());

        assertThat(maintenance.recoverStuckDispatching(Duration.ofMinutes(5))).isZero();
    }

    @Test
    void cleanUpTerminalMessagesDeletesPublishedAndDeadLetteredRows() {
        RecordingStore store = new RecordingStore();
        store.deleteResults.add(2);
        store.deleteResults.add(1);
        CountingTransactionRunner transactionRunner = new CountingTransactionRunner();
        DefaultOutboxMaintenance maintenance = new DefaultOutboxMaintenance(store, transactionRunner);

        int deleted = maintenance.cleanUpTerminalMessages(7, 30, 1000);

        assertThat(deleted).isEqualTo(3);
        assertThat(store.deletedStatuses)
                .containsExactly(OutboxMessageStatus.PUBLISHED, OutboxMessageStatus.DEAD_LETTERED);
        assertThat(transactionRunner.options).hasSize(2).allSatisfy(options -> {
            assertThat(options.name()).isEmpty();
            assertThat(options.propagation()).isEqualTo(TransactionPropagation.REQUIRES_NEW);
        });
    }

    @Test
    void cleanUpTerminalMessagesRejectsInvalidConfiguration() {
        DefaultOutboxMaintenance maintenance = new DefaultOutboxMaintenance(new RecordingStore(), new CountingTransactionRunner());

        assertThatThrownBy(() -> maintenance.cleanUpTerminalMessages(-1, 30, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> maintenance.cleanUpTerminalMessages(7, 30, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class CountingTransactionRunner implements TransactionRunner {
        private final List<TransactionOptions> options = new ArrayList<>();

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) {
            this.options.add(options);
            return callback.execute();
        }
    }

    private static final class RecordingStore implements OutboxMessageStore {
        private int recoverResult;
        private Instant recoverCutoff;
        private final List<Integer> deleteResults = new ArrayList<>();
        private final List<OutboxMessageStatus> deletedStatuses = new ArrayList<>();

        @Override
        public void append(OutboxMessage message) {
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            return List.of();
        }

        @Override
        public void markAsPublished(String eventId) {
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        }

        @Override
        public void reactivate(String eventId) {
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            recoverCutoff = cutoff;
            return recoverResult;
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
            deletedStatuses.add(status);
            if (deleteResults.isEmpty()) {
                return 0;
            }
            return deleteResults.removeFirst();
        }
    }
}
