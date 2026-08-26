package org.jfoundry.application.outbox;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Describes an external message to append to the transactional Outbox.
 *
 * @param eventId stable message identifier used for delivery idempotency
 * @param topic broker-neutral destination name
 * @param payloadKey optional partitioning or routing key
 * @param payloadType stable external message type name
 * @param payload message payload to serialize
 * @param occurredAt time the external message occurred
 * @param aggregateType optional source aggregate type
 * @param aggregateId optional source aggregate identifier
 * @param aggregateVersion optional source aggregate persistence version
 */
public record OutboxAppendRequest(
        String eventId,
        String topic,
        @Nullable String payloadKey,
        String payloadType,
        Object payload,
        Instant occurredAt,
        @Nullable String aggregateType,
        @Nullable String aggregateId,
        @Nullable Long aggregateVersion) {

    /**
     * Creates a request without aggregate routing metadata.
     */
    public static OutboxAppendRequest of(
            String eventId,
            String topic,
            @Nullable String payloadKey,
            String payloadType,
            Object payload,
            Instant occurredAt) {
        return new OutboxAppendRequest(
                eventId,
                topic,
                payloadKey,
                payloadType,
                payload,
                occurredAt,
                null,
                null,
                null);
    }
}
