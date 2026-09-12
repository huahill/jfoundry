package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.PayloadSerializer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenericOutboxStandaloneTest {

    @Test
    void appendsAnArbitraryPayloadWithoutDomainEventTypes() {
        RecordingStore store = new RecordingStore();
        PayloadSerializer serializer = payload -> "payload:" + payload;
        OutboxTemplate template = new OutboxTemplate(store, serializer);

        template.append(OutboxAppendRequest.of(
                "message-1",
                "billing.events.v1",
                "account-1",
                "InvoiceIssuedV1",
                "invoice-1",
                Instant.parse("2026-09-11T00:00:00Z")));

        assertThat(store.appended).isNotNull();
        assertThat(store.appended.getEventId()).isEqualTo("message-1");
        assertThat(store.appended.getPayloadJson()).isEqualTo("payload:invoice-1");
        assertThat(store.appended.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
    }

    private static final class RecordingStore implements OutboxMessageStore {

        private OutboxMessage appended;

        @Override
        public void append(OutboxMessage entry) {
            appended = entry;
        }

        @Override
        public void markAsPublished(String eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reactivate(String eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus status, Instant cutoff, int batchSize) {
            throw new UnsupportedOperationException();
        }
    }
}
