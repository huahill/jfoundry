package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.jfoundry.application.inbox.InboxMessage;
import org.jfoundry.application.inbox.InboxMessageStatus;

import java.time.Instant;
import java.util.UUID;

/// Jakarta Persistence mapping for a single Inbox message.
@Entity
@Table(name = "jfoundry_inbox_message", uniqueConstraints =
        @UniqueConstraint(name = "uk_inbox_consumer_message", columnNames = {"consumer_name", "message_id"}))
public class JpaInboxMessageEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @Column(name = "consumer_name", nullable = false, length = 255)
    private String consumerName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "processed_at", columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant processedAt;

    @Column(name = "claimed_at", columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant claimedAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp")
    @Convert(converter = InstantUtcConverter.class)
    private Instant updatedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    public static JpaInboxMessageEntity fromMessage(InboxMessage message) {
        JpaInboxMessageEntity entity = new JpaInboxMessageEntity();
        entity.id = UUID.randomUUID().toString();
        entity.apply(message);
        return entity;
    }

    public InboxMessage toMessage() {
        InboxMessage message = new InboxMessage();
        message.setMessageId(messageId);
        message.setConsumerName(consumerName);
        if (status != null) {
            message.setStatus(InboxMessageStatus.valueOf(status));
        }
        message.setProcessedAt(processedAt);
        message.setClaimedAt(claimedAt);
        message.setClaimToken(claimToken);
        message.setCreatedAt(createdAt);
        message.setUpdatedAt(updatedAt);
        message.setErrorMessage(errorMessage);
        return message;
    }

    public void apply(InboxMessage message) {
        messageId = message.getMessageId();
        consumerName = message.getConsumerName();
        InboxMessageStatus messageStatus = message.getStatus();
        status = messageStatus == null ? null : messageStatus.name();
        processedAt = message.getProcessedAt();
        claimedAt = message.getClaimedAt();
        claimToken = message.getClaimToken();
        createdAt = message.getCreatedAt();
        updatedAt = message.getUpdatedAt();
        errorMessage = message.getErrorMessage();
    }
}
