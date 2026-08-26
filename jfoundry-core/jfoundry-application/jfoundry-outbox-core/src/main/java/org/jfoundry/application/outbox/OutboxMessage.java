package org.jfoundry.application.outbox;

import org.jfoundry.application.messaging.MessagePropagation;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.time.Instant;

/// Outbox SPI data object plus state-machine methods.
/// <p>
/// Fields correspond to {@code jfoundry_outbox_event}, but this class carries no
/// ORM annotations. Concrete persistence implementations own table names,
/// primary-key strategy, and storage mapping, for example {@code OutboxData} in
/// the MyBatis-Plus adapter.
/// <p>
/// State transitions (PENDING / DISPATCHING / FAILED / PUBLISHED / DEAD_LETTERED)
/// are encapsulated by
/// {@link #markPublished()} / {@link #markFailed(String, int, BackoffStrategy)} /
/// {@link #reactivate()}. Entering and leaving {@code DISPATCHING} is controlled
/// by atomic claim and recovery operations, see {@code claimDispatchable}.
@NullUnmarked
public class OutboxMessage {

    private String eventId;
    private String topic;
    private @Nullable String payloadKey;
    private String payloadType;
    private String payloadJson;
    private MessagePropagation propagation = MessagePropagation.empty();
    private @Nullable String aggregateType;
    private @Nullable String aggregateId;
    private @Nullable Long aggregateVersion;
    private String status;
    private int retryCount;
    private @Nullable String errorMessage;
    private Instant occurredAt;
    private @Nullable Instant lastAttemptAt;
    private @Nullable Instant nextRetryAt;
    private Instant createdAt;
    private Instant updatedAt;
    /// Time of the latest successful atomic claim. Used with
    /// {@code idx_outbox_claim (status, claimed_at)} to detect and recover stuck
    /// DISPATCHING records.
    private @Nullable Instant claimedAt;
    /// Identifier of the pod that claimed this entry, usually hostname plus short UUID.
    private @Nullable String claimedBy;
    /// Unique token generated for the current {@code claimDispatchable} call.
    /// <p>
    /// Read-back matches this token exactly so stale DISPATCHING records from a
    /// previous batch, potentially left behind after a failed state update, are
    /// not mixed into the current batch. Cleared when leaving DISPATCHING.
    private @Nullable String claimToken;

    public static OutboxMessage newPending(String eventId, String topic, @Nullable String payloadKey,
                                          String payloadType, String payloadJson, Instant occurredAt) {
        OutboxMessage entry = new OutboxMessage();
        Instant now = Instant.now();
        entry.eventId = eventId;
        entry.topic = topic;
        entry.payloadKey = payloadKey;
        entry.payloadType = payloadType;
        entry.payloadJson = payloadJson;
        entry.status = OutboxMessageStatus.PENDING.name();
        entry.retryCount = 0;
        entry.errorMessage = null;
        entry.occurredAt = occurredAt;
        entry.lastAttemptAt = null;
        entry.nextRetryAt = null;
        entry.createdAt = now;
        entry.updatedAt = now;
        return entry;
    }

    public static OutboxMessage newPending(String eventId, String topic, @Nullable String payloadKey,
                                          String payloadType, String payloadJson, Instant occurredAt,
                                          @Nullable String aggregateType, @Nullable String aggregateId,
                                          @Nullable Long aggregateVersion) {
        OutboxMessage entry = newPending(eventId, topic, payloadKey, payloadType, payloadJson, occurredAt);
        entry.aggregateType = aggregateType;
        entry.aggregateId = aggregateId;
        entry.aggregateVersion = aggregateVersion;
        return entry;
    }

    public void markPublished() {
        Instant now = Instant.now();
        this.status = OutboxMessageStatus.PUBLISHED.name();
        this.lastAttemptAt = now;
        // Claim finished: this record is no longer owned by any pod.
        this.claimedAt = null;
        this.claimedBy = null;
        this.claimToken = null;
        this.updatedAt = now;
    }

    public void markFailed(@Nullable String errorMessage, int maxRetries, BackoffStrategy backoff) {
        int retryCountBefore = this.retryCount;
        Instant now = Instant.now();
        this.lastAttemptAt = now;
        this.errorMessage = errorMessage;
        java.time.Duration delay = backoff.nextDelay(retryCountBefore);
        this.retryCount = retryCountBefore + 1;
        if (this.retryCount >= maxRetries) {
            this.status = OutboxMessageStatus.DEAD_LETTERED.name();
            this.nextRetryAt = null;
        } else {
            this.status = OutboxMessageStatus.FAILED.name();
            this.nextRetryAt = now.plus(delay);
        }
        // Claim finished (DISPATCHING -> FAILED / DEAD_LETTERED): the next retry
        // will claim it again from this or another pod.
        this.claimedAt = null;
        this.claimedBy = null;
        this.claimToken = null;
        this.updatedAt = now;
    }

    public void reactivate() {
        if (!OutboxMessageStatus.DEAD_LETTERED.name().equals(this.status)) {
            throw new IllegalStateException(
                    "reactivate is only allowed from DEAD_LETTERED to PENDING; current status: " + this.status);
        }
        Instant now = Instant.now();
        this.status = OutboxMessageStatus.PENDING.name();
        this.retryCount = 0;
        this.nextRetryAt = now;
        this.errorMessage = null;
        // Defensive consistency: DEAD_LETTERED should already have no claim owner.
        this.claimedAt = null;
        this.claimedBy = null;
        this.claimToken = null;
        this.updatedAt = now;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public @Nullable String getPayloadKey() { return payloadKey; }
    public void setPayloadKey(@Nullable String payloadKey) { this.payloadKey = payloadKey; }
    public String getPayloadType() { return payloadType; }
    public void setPayloadType(String payloadType) { this.payloadType = payloadType; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public MessagePropagation getPropagation() { return propagation; }
    public void setPropagation(@Nullable MessagePropagation propagation) {
        this.propagation = propagation == null ? MessagePropagation.empty() : propagation;
    }
    public @Nullable String getAggregateType() { return aggregateType; }
    public void setAggregateType(@Nullable String aggregateType) { this.aggregateType = aggregateType; }
    public @Nullable String getAggregateId() { return aggregateId; }
    public void setAggregateId(@Nullable String aggregateId) { this.aggregateId = aggregateId; }
    public @Nullable Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(@Nullable Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public OutboxMessageStatus getStatus() { return OutboxMessageStatus.valueOf(status); }
    public void setStatus(OutboxMessageStatus status) { this.status = status.name(); }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public @Nullable String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(@Nullable String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public @Nullable Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(@Nullable Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public @Nullable Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(@Nullable Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public @Nullable Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(@Nullable Instant claimedAt) { this.claimedAt = claimedAt; }
    public @Nullable String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(@Nullable String claimedBy) { this.claimedBy = claimedBy; }
    public @Nullable String getClaimToken() { return claimToken; }
    public void setClaimToken(@Nullable String claimToken) { this.claimToken = claimToken; }
}
