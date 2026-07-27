package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.transaction.TransactionCallback;
import org.jfoundry.application.transaction.TransactionOptions;
import org.jfoundry.application.transaction.TransactionPropagation;
import org.jfoundry.application.transaction.TransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOutboxDispatchServiceTest {

    private final RecordingOutboxMessageStore store = new RecordingOutboxMessageStore();
    private final BackoffStrategy backoff = attempts -> Duration.ofSeconds(attempts);

    @Test
    void marksClaimedMessagesPublishedWhenSendSucceeds() {
        store.messages = List.of(message("evt-1"));
        DefaultOutboxDispatchService service = new DefaultOutboxDispatchService(
                store, message -> SendResult.ok(), 3, backoff, "pod-1");

        service.dispatch(10);

        assertThat(store.claimBatchSize).isEqualTo(10);
        assertThat(store.claimerId).isEqualTo("pod-1");
        assertThat(store.published).containsExactly("evt-1");
        assertThat(store.failed).isEmpty();
    }

    @Test
    void marksMessageFailedWhenSendReturnsFailure() {
        store.messages = List.of(message("evt-1"));
        MessageSender sender = message -> SendResult.fail("broker unavailable");
        DefaultOutboxDispatchService service = new DefaultOutboxDispatchService(store, sender, 5, backoff, "pod-1");

        service.dispatch(1);

        assertThat(store.failed).containsExactly("evt-1:broker unavailable:5");
        assertThat(store.published).isEmpty();
    }

    @Test
    void marksMessageFailedWhenSenderThrowsAndContinues() {
        store.messages = List.of(message("evt-1"), message("evt-2"));
        MessageSender sender = message -> {
            if ("key-1".equals(message.payloadKey())) {
                throw new IllegalStateException("boom");
            }
            return SendResult.ok();
        };
        DefaultOutboxDispatchService service = new DefaultOutboxDispatchService(store, sender, 2, backoff, "pod-1");

        service.dispatch(2);

        assertThat(store.failed).containsExactly("evt-1:boom:2");
        assertThat(store.published).containsExactly("evt-2");
    }

    @Test
    void claimsAndRecordsDeliveryInSeparateTransactionsWhileSendingOutsideThem() {
        store.messages = List.of(message("evt-1"));
        RecordingTransactionRunner transactions = new RecordingTransactionRunner();
        MessageSender sender = message -> {
            assertThat(transactions.inTransaction).isFalse();
            return SendResult.ok();
        };
        DefaultOutboxDispatchService service = new DefaultOutboxDispatchService(
                store, sender, transactions, 3, backoff, "pod-1");

        service.dispatch(10);

        assertThat(transactions.options)
                .extracting(TransactionOptions::propagation)
                .containsExactly(TransactionPropagation.REQUIRES_NEW, TransactionPropagation.REQUIRES_NEW);
        assertThat(transactions.options)
                .allSatisfy(options -> assertThat(options.name()).isEmpty());
        assertThat(store.published).containsExactly("evt-1");
    }

    @Test
    void sendsThePropagationStoredWithTheOutboxMessage() {
        OutboxMessage message = message("evt-1");
        message.setPropagation(MessagePropagation.from(java.util.Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")));
        store.messages = List.of(message);
        java.util.concurrent.atomic.AtomicReference<org.jfoundry.application.messaging.OutboundMessage> sent =
                new java.util.concurrent.atomic.AtomicReference<>();
        DefaultOutboxDispatchService service = new DefaultOutboxDispatchService(
                store, outbound -> {
                    sent.set(outbound);
                    return SendResult.ok();
                }, 3, backoff, "pod-1");

        service.dispatch(1);

        assertThat(sent.get().propagation()).isEqualTo(message.getPropagation());
    }

    private OutboxMessage message(String eventId) {
        String key = eventId.replace("evt-", "key-");
        return OutboxMessage.newPending(eventId, "topic", key, "type", "{}", Instant.now());
    }

    private static class RecordingOutboxMessageStore implements OutboxMessageStore {
        private List<OutboxMessage> messages = List.of();
        private int claimBatchSize;
        private String claimerId;
        private final java.util.List<String> published = new java.util.ArrayList<>();
        private final java.util.List<String> failed = new java.util.ArrayList<>();

        @Override
        public void append(OutboxMessage message) {
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            return messages.subList(0, Math.min(limit, messages.size()));
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            this.claimBatchSize = limit;
            this.claimerId = claimerId;
            return findDispatchable(limit, Instant.now());
        }

        @Override
        public void markAsPublished(String eventId) {
            published.add(eventId);
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
            failed.add(eventId + ":" + errorMessage + ":" + maxRetries);
        }

        @Override
        public void reactivate(String eventId) {
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

    private static final class RecordingTransactionRunner implements TransactionRunner {
        private final java.util.List<TransactionOptions> options = new java.util.ArrayList<>();
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
