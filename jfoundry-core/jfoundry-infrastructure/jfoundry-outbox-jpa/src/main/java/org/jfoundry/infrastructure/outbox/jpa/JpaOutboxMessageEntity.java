package org.jfoundry.infrastructure.outbox.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/// Jakarta Persistence mapping for a single Outbox message.
@Entity
@Table(name = "jfoundry_outbox_event")
public class JpaOutboxMessageEntity {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "payload_key", length = 255)
    private String payloadKey;

    @Column(name = "payload_type", nullable = false, length = 500)
    private String payloadType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(name = "traceparent", length = 512)
    private String traceparent;

    @Column(name = "tracestate", length = 512)
    private String tracestate;

    @Column(name = "aggregate_type", length = 255)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 255)
    private String aggregateId;

    @Column(name = "aggregate_version")
    private Long aggregateVersion;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant occurredAt;

    @Column(name = "last_attempt_at", columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant lastAttemptAt;

    @Column(name = "next_retry_at", columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant nextRetryAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant updatedAt;

    @Column(name = "claimed_at", columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant claimedAt;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    public static JpaOutboxMessageEntity fromMessage(OutboxMessage message) {
        JpaOutboxMessageEntity entity = new JpaOutboxMessageEntity();
        entity.apply(message);
        return entity;
    }

    public OutboxMessage toMessage() {
        OutboxMessage message = new OutboxMessage();
        message.setEventId(eventId);
        message.setTopic(topic);
        message.setPayloadKey(payloadKey);
        message.setPayloadType(payloadType);
        message.setPayloadJson(payloadJson);
        Map<String, String> propagation = new LinkedHashMap<>();
        if (traceparent != null) {
            propagation.put("traceparent", traceparent);
        }
        if (tracestate != null) {
            propagation.put("tracestate", tracestate);
        }
        message.setPropagation(MessagePropagation.from(propagation));
        message.setAggregateType(aggregateType);
        message.setAggregateId(aggregateId);
        message.setAggregateVersion(aggregateVersion);
        if (status != null) {
            message.setStatus(OutboxMessageStatus.valueOf(status));
        }
        message.setRetryCount(retryCount);
        message.setErrorMessage(errorMessage);
        message.setOccurredAt(occurredAt);
        message.setLastAttemptAt(lastAttemptAt);
        message.setNextRetryAt(nextRetryAt);
        message.setCreatedAt(createdAt);
        message.setUpdatedAt(updatedAt);
        message.setClaimedAt(claimedAt);
        message.setClaimedBy(claimedBy);
        message.setClaimToken(claimToken);
        return message;
    }

    public void apply(OutboxMessage message) {
        eventId = message.getEventId();
        topic = message.getTopic();
        payloadKey = message.getPayloadKey();
        payloadType = message.getPayloadType();
        payloadJson = message.getPayloadJson();
        traceparent = message.getPropagation().entries().get("traceparent");
        tracestate = message.getPropagation().entries().get("tracestate");
        aggregateType = message.getAggregateType();
        aggregateId = message.getAggregateId();
        aggregateVersion = message.getAggregateVersion();
        OutboxMessageStatus messageStatus = message.getStatus();
        status = messageStatus == null ? null : messageStatus.name();
        retryCount = message.getRetryCount();
        errorMessage = message.getErrorMessage();
        occurredAt = message.getOccurredAt();
        lastAttemptAt = message.getLastAttemptAt();
        nextRetryAt = message.getNextRetryAt();
        createdAt = message.getCreatedAt();
        updatedAt = message.getUpdatedAt();
        claimedAt = message.getClaimedAt();
        claimedBy = message.getClaimedBy();
        claimToken = message.getClaimToken();
    }

    public String getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }
}
