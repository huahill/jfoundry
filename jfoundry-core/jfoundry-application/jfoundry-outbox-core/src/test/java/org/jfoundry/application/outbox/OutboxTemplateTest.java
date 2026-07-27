package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.PayloadSerializer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTemplateTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-13T08:00:00Z");

    private final RecordingStore store = new RecordingStore();
    private final PayloadSerializer serializer = payload -> "json:" + ((ApprovedPayload) payload).claimId();
    private final OutboxTemplate template = new OutboxTemplate(store, serializer);

    @Test
    void exposesOutboxRecorderContract() {
        assertThat(template).isInstanceOf(OutboxRecorder.class);
    }

    @Test
    void appendsSerializedPayloadAndAggregateMetadata() {
        OutboxAppendRequest request = new OutboxAppendRequest(
                "event-1",
                "expense-approval.events.v1",
                "claim-1",
                "ExpenseClaimApprovedV1",
                new ApprovedPayload("claim-1"),
                OCCURRED_AT,
                "ExpenseClaim",
                "claim-1",
                4L);

        template.append(request);

        assertThat(store.appended.getEventId()).isEqualTo("event-1");
        assertThat(store.appended.getTopic()).isEqualTo("expense-approval.events.v1");
        assertThat(store.appended.getPayloadKey()).isEqualTo("claim-1");
        assertThat(store.appended.getPayloadType()).isEqualTo("ExpenseClaimApprovedV1");
        assertThat(store.appended.getPayloadJson()).isEqualTo("json:claim-1");
        assertThat(store.appended.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(store.appended.getAggregateType()).isEqualTo("ExpenseClaim");
        assertThat(store.appended.getAggregateId()).isEqualTo("claim-1");
        assertThat(store.appended.getAggregateVersion()).isEqualTo(4L);
        assertThat(store.appended.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
    }

    @Test
    void appendsMessageWithoutAggregateMetadata() {
        template.append(OutboxAppendRequest.of(
                "event-2",
                "expense-approval.events.v1",
                null,
                "ExpenseClaimApprovedV1",
                new ApprovedPayload("claim-2"),
                OCCURRED_AT));

        assertThat(store.appended.getAggregateType()).isNull();
        assertThat(store.appended.getAggregateId()).isNull();
        assertThat(store.appended.getAggregateVersion()).isNull();
    }

    @Test
    void rejectsBlankRequiredText() {
        OutboxAppendRequest valid = validRequest();

        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                " ", valid.topic(), valid.payloadKey(), valid.payloadType(), valid.payload(),
                valid.occurredAt(), valid.aggregateType(), valid.aggregateId(), valid.aggregateVersion())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventId must not be blank");
        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), " ", valid.payloadKey(), valid.payloadType(), valid.payload(),
                valid.occurredAt(), valid.aggregateType(), valid.aggregateId(), valid.aggregateVersion())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topic must not be blank");
        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), valid.topic(), valid.payloadKey(), " ", valid.payload(),
                valid.occurredAt(), valid.aggregateType(), valid.aggregateId(), valid.aggregateVersion())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payloadType must not be blank");
    }

    @Test
    void rejectsNullPayloadAndOccurredAt() {
        OutboxAppendRequest valid = validRequest();

        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), valid.topic(), valid.payloadKey(), valid.payloadType(), null,
                valid.occurredAt(), valid.aggregateType(), valid.aggregateId(), valid.aggregateVersion())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("payload must not be null");
        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), valid.topic(), valid.payloadKey(), valid.payloadType(), valid.payload(),
                null, valid.aggregateType(), valid.aggregateId(), valid.aggregateVersion())))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("occurredAt must not be null");
    }

    @Test
    void rejectsIncompleteAggregateMetadata() {
        OutboxAppendRequest valid = validRequest();

        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), valid.topic(), valid.payloadKey(), valid.payloadType(), valid.payload(),
                valid.occurredAt(), "ExpenseClaim", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType and aggregateId must both be provided or both be absent");
        assertThatThrownBy(() -> template.append(new OutboxAppendRequest(
                valid.eventId(), valid.topic(), valid.payloadKey(), valid.payloadType(), valid.payload(),
                valid.occurredAt(), null, null, 4L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateVersion requires aggregate metadata");
    }

    @Test
    void rejectsNullConstructorDependenciesAndRequest() {
        assertThatThrownBy(() -> new OutboxTemplate(null, serializer))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("store must not be null");
        assertThatThrownBy(() -> new OutboxTemplate(store, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("serializer must not be null");
        assertThatThrownBy(() -> template.append(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("request must not be null");
    }

    private static OutboxAppendRequest validRequest() {
        return new OutboxAppendRequest(
                "event-1",
                "expense-approval.events.v1",
                "claim-1",
                "ExpenseClaimApprovedV1",
                new ApprovedPayload("claim-1"),
                OCCURRED_AT,
                "ExpenseClaim",
                "claim-1",
                4L);
    }

    private record ApprovedPayload(String claimId) {
    }

    private static final class RecordingStore implements OutboxMessageStore {

        private OutboxMessage appended;

        @Override
        public void append(OutboxMessage entry) {
            this.appended = entry;
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAsPublished(String eventId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markAsFailed(
                String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
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
