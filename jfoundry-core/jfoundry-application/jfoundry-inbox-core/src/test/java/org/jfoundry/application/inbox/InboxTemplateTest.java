package org.jfoundry.application.inbox;

import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxTemplateTest {

    @Test
    void exposesInboxMessageProcessorContract() {
        assertThat(new InboxTemplate(new StubStore(InboxClaim.duplicate())))
                .isInstanceOf(InboxMessageProcessor.class);
    }

    @Test
    void returnsDuplicateWithoutRunningHandler() {
        StubStore store = new StubStore(InboxClaim.duplicate());
        AtomicBoolean called = new AtomicBoolean();

        InboxExecutionResult result = new InboxTemplate(store).executeOnce("evt-1", "projection", () -> called.set(true));

        assertThat(result).isEqualTo(InboxExecutionResult.DUPLICATE);
        assertThat(called).isFalse();
    }

    @Test
    void returnsInProgressWithoutRunningHandler() {
        StubStore store = new StubStore(InboxClaim.inProgress());

        assertThat(new InboxTemplate(store).executeOnce("evt-1", "projection", () -> {}))
                .isEqualTo(InboxExecutionResult.IN_PROGRESS);
    }

    @Test
    void usesSeparateTransactionsForClaimAndCompletion() {
        RecordingTransactionRunner transactions = new RecordingTransactionRunner();
        StubStore store = new StubStore(InboxClaim.fresh("claim-1"));

        InboxExecutionResult result = new InboxTemplate(store, transactions)
                .executeOnce("evt-1", "projection", () -> assertThat(transactions.inTransaction).isTrue());

        assertThat(result).isEqualTo(InboxExecutionResult.PROCESSED);
        assertThat(store.processedToken).isEqualTo("claim-1");
        assertThat(transactions.options).extracting(TransactionOptions::propagation)
                .containsExactly(TransactionPropagation.REQUIRES_NEW, TransactionPropagation.REQUIRES_NEW);
    }

    @Test
    void recordsFailureInIndependentTransactionAndRethrows() {
        RecordingTransactionRunner transactions = new RecordingTransactionRunner();
        StubStore store = new StubStore(InboxClaim.fresh("claim-1"));

        assertThatThrownBy(() -> new InboxTemplate(store, transactions).executeOnce("evt-1", "projection", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

        assertThat(store.failedToken).isEqualTo("claim-1");
        assertThat(transactions.options).extracting(TransactionOptions::propagation)
                .containsExactly(TransactionPropagation.REQUIRES_NEW, TransactionPropagation.REQUIRES_NEW,
                        TransactionPropagation.REQUIRES_NEW);
    }

    private static final class StubStore implements InboxMessageStore {
        private final InboxClaim claim;
        private String processedToken;
        private String failedToken;

        private StubStore(InboxClaim claim) {
            this.claim = claim;
        }

        @Override
        public InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration) {
            return claim;
        }

        @Override
        public boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now) {
            processedToken = claimToken;
            return true;
        }

        @Override
        public boolean markFailed(String messageId, String consumerName, String claimToken, String errorMessage,
                                  Instant now) {
            failedToken = claimToken;
            return true;
        }
    }

    private static final class RecordingTransactionRunner implements TransactionRunner {
        private final List<TransactionOptions> options = new ArrayList<>();
        private boolean inTransaction;

        @Override
        public <T> T call(TransactionOptions options, TransactionCallback<T> callback) throws Exception {
            this.options.add(options);
            inTransaction = true;
            try {
                return callback.execute();
            } finally {
                inTransaction = false;
            }
        }
    }
}
