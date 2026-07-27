package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.PayloadSerializer;

import java.util.Objects;

/// Programmatic entry point for recording explicit integration messages in an Outbox.
/// <p>
/// The template serializes and appends within the caller's transaction. It does not translate domain
/// events, start a transaction, or publish to a broker.
public final class OutboxTemplate implements OutboxRecorder {

    private final OutboxMessageStore store;
    private final PayloadSerializer serializer;

    public OutboxTemplate(OutboxMessageStore store, PayloadSerializer serializer) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.serializer = Objects.requireNonNull(serializer, "serializer must not be null");
    }

    /// Serializes and appends one pending Outbox message.
    @Override
    public void append(OutboxAppendRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.eventId(), "eventId");
        requireText(request.topic(), "topic");
        requireText(request.payloadType(), "payloadType");
        Objects.requireNonNull(request.payload(), "payload must not be null");
        Objects.requireNonNull(request.occurredAt(), "occurredAt must not be null");
        validateAggregateMetadata(request);

        String payloadJson = serializer.serialize(request.payload());
        store.append(OutboxMessage.newPending(
                request.eventId(),
                request.topic(),
                request.payloadKey(),
                request.payloadType(),
                payloadJson,
                request.occurredAt(),
                request.aggregateType(),
                request.aggregateId(),
                request.aggregateVersion()));
    }

    private static void validateAggregateMetadata(OutboxAppendRequest request) {
        boolean hasType = request.aggregateType() != null;
        boolean hasId = request.aggregateId() != null;
        if (hasType != hasId) {
            throw new IllegalArgumentException(
                    "aggregateType and aggregateId must both be provided or both be absent");
        }
        if (hasType) {
            requireText(request.aggregateType(), "aggregateType");
            requireText(request.aggregateId(), "aggregateId");
        }
        if (request.aggregateVersion() != null && !hasType) {
            throw new IllegalArgumentException("aggregateVersion requires aggregate metadata");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
