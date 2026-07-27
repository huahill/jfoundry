package org.jfoundry.infrastructure.outbox.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/// MyBatis-Plus persistence data object for the Outbox table.
/// <p>
/// Fields correspond one-to-one with SPI-level {@link OutboxMessage} fields, but this type carries
/// MyBatis-Plus annotations ({@code @TableName("jfoundry_outbox_event")} and
/// {@code @TableId(type = IdType.INPUT)}). The SPI layer is not bound to any ORM; this class fixes
/// Outbox fields as a MyBatis-Plus entity view, and {@link MybatisPlusOutboxMessageStore} converts
/// entry ↔ data at the boundary.
@TableName("jfoundry_outbox_event")
public class OutboxData {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String topic;
    private String payloadKey;
    private String payloadType;
    private String payloadJson;
    private String traceparent;
    private String tracestate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private int retryCount;
    private String errorMessage;
    private Instant occurredAt;
    private Instant lastAttemptAt;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant updatedAt;
    /// Atomic claim timestamp aligned with OutboxMessage.claimedAt.
    private Instant claimedAt;
    /// Pod identifier that claimed this entry, aligned with OutboxMessage.claimedBy.
    private String claimedBy;
    /// Unique token generated for this claimDispatchable call, aligned with OutboxMessage.claimToken.
    private String claimToken;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPayloadKey() { return payloadKey; }
    public void setPayloadKey(String payloadKey) { this.payloadKey = payloadKey; }
    public String getPayloadType() { return payloadType; }
    public void setPayloadType(String payloadType) { this.payloadType = payloadType; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
    public String getTraceparent() { return traceparent; }
    public void setTraceparent(String traceparent) { this.traceparent = traceparent; }
    public String getTracestate() { return tracestate; }
    public void setTracestate(String tracestate) { this.tracestate = tracestate; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public String getClaimToken() { return claimToken; }
    public void setClaimToken(String claimToken) { this.claimToken = claimToken; }

    /// Converts an SPI entry to MP data.
    /// <p>
    /// The status string is copied in raw form, preserving the OutboxMessageStatus.name() literal;
    /// consistency is maintained by the SPI entry itself.
    public static OutboxData fromMessage(OutboxMessage entry) {
        OutboxData data = new OutboxData();
        data.eventId = entry.getEventId();
        data.topic = entry.getTopic();
        data.payloadKey = entry.getPayloadKey();
        data.payloadType = entry.getPayloadType();
        data.payloadJson = entry.getPayloadJson();
        data.traceparent = entry.getPropagation().entries().get("traceparent");
        data.tracestate = entry.getPropagation().entries().get("tracestate");
        data.aggregateType = entry.getAggregateType();
        data.aggregateId = entry.getAggregateId();
        data.aggregateVersion = entry.getAggregateVersion();
        OutboxMessageStatus status = entry.getStatus();
        data.status = status != null ? status.name() : null;
        data.retryCount = entry.getRetryCount();
        data.errorMessage = entry.getErrorMessage();
        data.occurredAt = entry.getOccurredAt();
        data.lastAttemptAt = entry.getLastAttemptAt();
        data.nextRetryAt = entry.getNextRetryAt();
        data.createdAt = entry.getCreatedAt();
        data.updatedAt = entry.getUpdatedAt();
        data.claimedAt = entry.getClaimedAt();
        data.claimedBy = entry.getClaimedBy();
        data.claimToken = entry.getClaimToken();
        return data;
    }

    /// Converts MP data to an SPI entry.
    public static OutboxMessage toMessage(OutboxData data) {
        OutboxMessage entry = new OutboxMessage();
        entry.setEventId(data.eventId);
        entry.setTopic(data.topic);
        entry.setPayloadKey(data.payloadKey);
        entry.setPayloadType(data.payloadType);
        entry.setPayloadJson(data.payloadJson);
        Map<String, String> propagation = new LinkedHashMap<>();
        if (data.traceparent != null) {
            propagation.put("traceparent", data.traceparent);
        }
        if (data.tracestate != null) {
            propagation.put("tracestate", data.tracestate);
        }
        entry.setPropagation(MessagePropagation.from(propagation));
        entry.setAggregateType(data.aggregateType);
        entry.setAggregateId(data.aggregateId);
        entry.setAggregateVersion(data.aggregateVersion);
        if (data.status != null) {
            entry.setStatus(OutboxMessageStatus.valueOf(data.status));
        }
        entry.setRetryCount(data.retryCount);
        entry.setErrorMessage(data.errorMessage);
        entry.setOccurredAt(data.occurredAt);
        entry.setLastAttemptAt(data.lastAttemptAt);
        entry.setNextRetryAt(data.nextRetryAt);
        entry.setCreatedAt(data.createdAt);
        entry.setUpdatedAt(data.updatedAt);
        entry.setClaimedAt(data.claimedAt);
        entry.setClaimedBy(data.claimedBy);
        entry.setClaimToken(data.claimToken);
        return entry;
    }
}
