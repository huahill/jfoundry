package org.jfoundry.application.inbox;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullUnmarked;

import java.time.Instant;

@NullUnmarked
public class InboxMessage {

    private String messageId;
    private String consumerName;
    private InboxMessageStatus status;
    private @Nullable Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private @Nullable Instant claimedAt;
    private @Nullable String claimToken;
    private @Nullable String errorMessage;

    public static InboxMessage processed(String messageId, String consumerName) {
        Instant now = Instant.now();
        InboxMessage message = new InboxMessage();
        message.messageId = messageId;
        message.consumerName = consumerName;
        message.status = InboxMessageStatus.PROCESSED;
        message.processedAt = now;
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }

    public static InboxMessage processing(String messageId, String consumerName) {
        Instant now = Instant.now();
        InboxMessage message = new InboxMessage();
        message.messageId = messageId;
        message.consumerName = consumerName;
        message.status = InboxMessageStatus.PROCESSING;
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getConsumerName() { return consumerName; }
    public void setConsumerName(String consumerName) { this.consumerName = consumerName; }
    public InboxMessageStatus getStatus() { return status; }
    public void setStatus(InboxMessageStatus status) { this.status = status; }
    public @Nullable Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(@Nullable Instant processedAt) { this.processedAt = processedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public @Nullable Instant getClaimedAt() { return claimedAt; }
    public void setClaimedAt(@Nullable Instant claimedAt) { this.claimedAt = claimedAt; }
    public @Nullable String getClaimToken() { return claimToken; }
    public void setClaimToken(@Nullable String claimToken) { this.claimToken = claimToken; }
    public @Nullable String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(@Nullable String errorMessage) { this.errorMessage = errorMessage; }
}
