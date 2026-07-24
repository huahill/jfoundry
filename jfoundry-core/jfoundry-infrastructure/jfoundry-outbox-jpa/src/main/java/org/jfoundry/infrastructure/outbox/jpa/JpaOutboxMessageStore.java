package org.jfoundry.infrastructure.outbox.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.application.outbox.OutboxMessageStore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Jakarta Persistence implementation of the Outbox persistence SPI.
public final class JpaOutboxMessageStore implements OutboxMessageStore {

    private final EntityManager entityManager;

    public JpaOutboxMessageStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(OutboxMessage entry) {
        entityManager.persist(JpaOutboxMessageEntity.fromMessage(entry));
    }

    @Override
    public List<OutboxMessage> findDispatchable(int limit, Instant now) {
        return dispatchableQuery(now)
                .setMaxResults(limit)
                .getResultStream()
                .map(JpaOutboxMessageEntity::toMessage)
                .toList();
    }

    @Override
    public void markAsPublished(String eventId) {
        JpaOutboxMessageEntity entity = entityManager.find(JpaOutboxMessageEntity.class, eventId);
        if (entity == null || !OutboxMessageStatus.DISPATCHING.name().equals(entity.getStatus())) {
            return;
        }
        OutboxMessage message = entity.toMessage();
        message.markPublished();
        entity.apply(message);
    }

    @Override
    public void markAsPublished(String eventId, String claimToken) {
        entityManager.createNativeQuery("""
                update jfoundry_outbox_event
                   set status = ?1, last_attempt_at = ?2, updated_at = ?2,
                       claimed_at = null, claimed_by = null, claim_token = null
                 where event_id = ?3 and status = ?4 and claim_token = ?5
                """)
                .setParameter(1, OutboxMessageStatus.PUBLISHED.name())
                .setParameter(2, utcTimestamp(Instant.now()))
                .setParameter(3, eventId)
                .setParameter(4, OutboxMessageStatus.DISPATCHING.name())
                .setParameter(5, claimToken)
                .executeUpdate();
        entityManager.clear();
    }

    @Override
    public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        JpaOutboxMessageEntity entity = entityManager.find(JpaOutboxMessageEntity.class, eventId);
        if (entity == null || !OutboxMessageStatus.DISPATCHING.name().equals(entity.getStatus())) {
            return;
        }
        OutboxMessage message = entity.toMessage();
        message.markFailed(errorMessage, maxRetries, backoff);
        entity.apply(message);
    }

    @Override
    public void markAsFailed(String eventId, String claimToken,
                             String errorMessage, int maxRetries, BackoffStrategy backoff) {
        JpaOutboxMessageEntity entity = entityManager.find(JpaOutboxMessageEntity.class, eventId);
        if (entity == null || !OutboxMessageStatus.DISPATCHING.name().equals(entity.getStatus())
                || !claimToken.equals(entity.toMessage().getClaimToken())) {
            return;
        }
        OutboxMessage message = entity.toMessage();
        message.markFailed(errorMessage, maxRetries, backoff);
        entityManager.createQuery("""
                update JpaOutboxMessageEntity e
                   set e.status = :status, e.retryCount = :retryCount, e.errorMessage = :errorMessage,
                       e.lastAttemptAt = :lastAttemptAt, e.nextRetryAt = :nextRetryAt,
                       e.updatedAt = :updatedAt, e.claimedAt = null, e.claimedBy = null, e.claimToken = null
                 where e.eventId = :eventId and e.status = :dispatching and e.claimToken = :claimToken
                """)
                .setParameter("status", message.getStatus().name())
                .setParameter("retryCount", message.getRetryCount())
                .setParameter("errorMessage", message.getErrorMessage())
                .setParameter("lastAttemptAt", message.getLastAttemptAt())
                .setParameter("nextRetryAt", message.getNextRetryAt())
                .setParameter("updatedAt", message.getUpdatedAt())
                .setParameter("eventId", eventId)
                .setParameter("dispatching", OutboxMessageStatus.DISPATCHING.name())
                .setParameter("claimToken", claimToken)
                .executeUpdate();
        entityManager.clear();
    }

    @Override
    public void reactivate(String eventId) {
        JpaOutboxMessageEntity entity = entityManager.find(JpaOutboxMessageEntity.class, eventId);
        if (entity == null) {
            return;
        }
        OutboxMessage message = entity.toMessage();
        message.reactivate();
        entity.apply(message);
    }

    @Override
    public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
        requirePositive(limit, "limit");
        requireText(claimerId, "claimerId");
        String claimToken = UUID.randomUUID().toString();
        Instant eligibilityTime = Instant.now();
        List<OutboxMessage> claimed = new ArrayList<>(limit);
        Set<String> attemptedEventIds = new HashSet<>();

        while (claimed.size() < limit) {
            int remaining = limit - claimed.size();
            List<OutboxMessage> candidates = dispatchableQuery(eligibilityTime, attemptedEventIds)
                    .setMaxResults(remaining)
                    .getResultStream()
                    .map(JpaOutboxMessageEntity::toMessage)
                    .toList();
            entityManager.clear();
            if (candidates.isEmpty()) {
                break;
            }
            for (OutboxMessage candidate : candidates) {
                attemptedEventIds.add(candidate.getEventId());
                Instant claimedAt = Instant.now();
                int updated = entityManager.createQuery("""
                        update JpaOutboxMessageEntity e
                           set e.status = :dispatching, e.claimedAt = :claimedAt,
                               e.claimedBy = :claimerId, e.claimToken = :claimToken
                         where e.eventId = :eventId and e.status = :candidateStatus
                        """)
                        .setParameter("dispatching", OutboxMessageStatus.DISPATCHING.name())
                        .setParameter("claimedAt", claimedAt)
                        .setParameter("claimerId", claimerId)
                        .setParameter("claimToken", claimToken)
                        .setParameter("eventId", candidate.getEventId())
                        .setParameter("candidateStatus", candidate.getStatus().name())
                        .executeUpdate();
                entityManager.clear();
                if (updated == 1) {
                    candidate.setStatus(OutboxMessageStatus.DISPATCHING);
                    candidate.setClaimedAt(claimedAt);
                    candidate.setClaimedBy(claimerId);
                    candidate.setClaimToken(claimToken);
                    claimed.add(candidate);
                }
            }
        }
        return claimed;
    }

    @Override
    public int recoverStuckDispatching(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff must not be null");
        }
        int recovered = entityManager.createQuery("""
                update JpaOutboxMessageEntity e
                   set e.status = :pending, e.claimedAt = null, e.claimedBy = null, e.claimToken = null
                 where e.status = :dispatching and e.claimedAt < :cutoff
                """)
                .setParameter("pending", OutboxMessageStatus.PENDING.name())
                .setParameter("dispatching", OutboxMessageStatus.DISPATCHING.name())
                .setParameter("cutoff", cutoff)
                .executeUpdate();
        entityManager.clear();
        return recovered;
    }

    @Override
    public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (status != OutboxMessageStatus.PUBLISHED && status != OutboxMessageStatus.DEAD_LETTERED) {
            throw new IllegalArgumentException("status must be terminal: " + status);
        }
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff must not be null");
        }
        requirePositive(batchSize, "batchSize");
        int deleted = 0;
        while (true) {
            List<String> ids = entityManager.createQuery("""
                    select e.eventId from JpaOutboxMessageEntity e
                     where e.status = :status and e.occurredAt < :cutoff
                     order by e.occurredAt asc, e.eventId asc
                    """, String.class)
                    .setParameter("status", status.name())
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(batchSize)
                    .getResultList();
            if (ids.isEmpty()) {
                return deleted;
            }
            int removed = entityManager.createQuery("""
                    delete from JpaOutboxMessageEntity e
                     where e.eventId in :ids and e.status = :status and e.occurredAt < :cutoff
                    """)
                    .setParameter("ids", ids)
                    .setParameter("status", status.name())
                    .setParameter("cutoff", cutoff)
                    .executeUpdate();
            entityManager.clear();
            deleted += removed;
        }
    }

    private jakarta.persistence.TypedQuery<JpaOutboxMessageEntity> dispatchableQuery(Instant now) {
        return dispatchableQuery(now, Set.of());
    }

    private jakarta.persistence.TypedQuery<JpaOutboxMessageEntity> dispatchableQuery(
            Instant now, Set<String> excludedEventIds) {
        String query = """
                select e from JpaOutboxMessageEntity e
                 where (e.status = :pending or e.status = :failed)
                   and (e.nextRetryAt is null or e.nextRetryAt <= :now)
                """ + (excludedEventIds.isEmpty() ? "" : " and e.eventId not in :excludedEventIds") + """
                 order by e.occurredAt asc, e.eventId asc
                """;
        jakarta.persistence.TypedQuery<JpaOutboxMessageEntity> typedQuery = entityManager.createQuery(
                query, JpaOutboxMessageEntity.class)
                .setParameter("pending", OutboxMessageStatus.PENDING.name())
                .setParameter("failed", OutboxMessageStatus.FAILED.name())
                .setParameter("now", now);
        if (!excludedEventIds.isEmpty()) {
            typedQuery.setParameter("excludedEventIds", excludedEventIds);
        }
        return typedQuery;
    }

    private static LocalDateTime utcTimestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
