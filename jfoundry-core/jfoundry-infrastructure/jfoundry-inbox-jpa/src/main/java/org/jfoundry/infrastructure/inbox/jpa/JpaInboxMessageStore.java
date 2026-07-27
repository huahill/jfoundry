package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.application.inbox.InboxMessageStore;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/// Jakarta Persistence implementation of the Inbox persistence SPI.
public final class JpaInboxMessageStore implements InboxMessageStore {

    private final EntityManager entityManager;
    private final JpaInboxClaimStrategy claimStrategy;

    public JpaInboxMessageStore(EntityManager entityManager, JpaInboxClaimStrategy claimStrategy) {
        this.entityManager = entityManager;
        this.claimStrategy = claimStrategy;
    }

    @Override
    public InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration) {
        String claimToken = UUID.randomUUID().toString();
        int retried = entityManager.createQuery("""
                update JpaInboxMessageEntity e
                   set e.status = :processing, e.claimedAt = :claimedAt, e.claimToken = :claimToken,
                       e.updatedAt = :updatedAt, e.errorMessage = null
                 where e.messageId = :messageId and e.consumerName = :consumerName and e.status = :failed
                """)
                .setParameter("processing", InboxMessageStatus.PROCESSING.name())
                .setParameter("claimedAt", now)
                .setParameter("claimToken", claimToken)
                .setParameter("updatedAt", now)
                .setParameter("messageId", messageId)
                .setParameter("consumerName", consumerName)
                .setParameter("failed", InboxMessageStatus.FAILED.name())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        if (retried == 1) {
            return InboxClaim.claimed(claimToken, InboxClaimSource.FAILED_RETRY);
        }
        int reclaimed = entityManager.createQuery("""
                update JpaInboxMessageEntity e
                   set e.claimedAt = :claimedAt, e.claimToken = :claimToken, e.updatedAt = :updatedAt
                 where e.messageId = :messageId and e.consumerName = :consumerName and e.status = :processing
                   and e.claimedAt < :expiredBefore
                """)
                .setParameter("claimedAt", now)
                .setParameter("claimToken", claimToken)
                .setParameter("updatedAt", now)
                .setParameter("messageId", messageId)
                .setParameter("consumerName", consumerName)
                .setParameter("processing", InboxMessageStatus.PROCESSING.name())
                .setParameter("expiredBefore", now.minus(leaseDuration))
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        if (reclaimed == 1) {
            return InboxClaim.claimed(claimToken, InboxClaimSource.EXPIRED_LEASE);
        }
        InboxMessageStatus status = statusOf(messageId, consumerName);
        if (status == InboxMessageStatus.PROCESSED) {
            return InboxClaim.duplicate();
        }
        if (status != null) {
            return InboxClaim.inProgress();
        }
        boolean claimed = claimStrategy.tryClaim(entityManager, messageId, consumerName, claimToken, now);
        entityManager.flush();
        entityManager.clear();
        return claimed ? InboxClaim.fresh(claimToken) : existingClaim(messageId, consumerName);
    }

    @Override
    public boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now) {
        int updated = entityManager.createQuery("""
                update JpaInboxMessageEntity e
                   set e.status = :processed, e.processedAt = :processedAt, e.updatedAt = :processedAt,
                       e.claimedAt = null, e.claimToken = null, e.errorMessage = null
                 where e.messageId = :messageId and e.consumerName = :consumerName and e.status = :processing
                   and e.claimToken = :claimToken
                """)
                .setParameter("processed", InboxMessageStatus.PROCESSED.name())
                .setParameter("processedAt", now)
                .setParameter("messageId", messageId)
                .setParameter("consumerName", consumerName)
                .setParameter("processing", InboxMessageStatus.PROCESSING.name())
                .setParameter("claimToken", claimToken)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return updated == 1;
    }

    @Override
    public boolean markFailed(String messageId, String consumerName, String claimToken,
                              String errorMessage, Instant now) {
        int updated = entityManager.createQuery("""
                update JpaInboxMessageEntity e
                   set e.status = :failed, e.updatedAt = :updatedAt, e.errorMessage = :errorMessage,
                       e.claimedAt = null, e.claimToken = null
                 where e.messageId = :messageId and e.consumerName = :consumerName and e.status = :processing
                   and e.claimToken = :claimToken
                """)
                .setParameter("failed", InboxMessageStatus.FAILED.name())
                .setParameter("updatedAt", now)
                .setParameter("errorMessage", errorMessage)
                .setParameter("messageId", messageId)
                .setParameter("consumerName", consumerName)
                .setParameter("processing", InboxMessageStatus.PROCESSING.name())
                .setParameter("claimToken", claimToken)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return updated == 1;
    }

    private InboxClaim existingClaim(String messageId, String consumerName) {
        return statusOf(messageId, consumerName) == InboxMessageStatus.PROCESSED
                ? InboxClaim.duplicate() : InboxClaim.inProgress();
    }

    private InboxMessageStatus statusOf(String messageId, String consumerName) {
        return entityManager.createQuery("""
                select e.status from JpaInboxMessageEntity e
                 where e.messageId = :messageId and e.consumerName = :consumerName
                """, String.class)
                .setParameter("messageId", messageId)
                .setParameter("consumerName", consumerName)
                .getResultStream()
                .findFirst()
                .map(InboxMessageStatus::valueOf)
                .orElse(null);
    }

}
