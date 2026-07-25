package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.application.inbox.InboxMessageStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/// Claims Inbox messages using MySQL's duplicate-tolerant insert syntax.
public final class MySqlJpaInboxClaimStrategy implements JpaInboxClaimStrategy {

    private static final InstantUtcConverter UTC_CONVERTER = new InstantUtcConverter();

    @Override
    public boolean tryClaim(EntityManager entityManager, String messageId, String consumerName, Instant now) {
        LocalDateTime utcNow = UTC_CONVERTER.convertToDatabaseColumn(now).toLocalDateTime();
        String attemptedId = UUID.randomUUID().toString();
        entityManager.createNativeQuery("""
                insert into jfoundry_inbox_message
                    (id, message_id, consumer_name, status, created_at, updated_at)
                values (?1, ?2, ?3, ?4, ?5, ?6)
                on duplicate key update id = id
                """)
                .setParameter(1, attemptedId)
                .setParameter(2, messageId)
                .setParameter(3, consumerName)
                .setParameter(4, InboxMessageStatus.PROCESSING.name())
                .setParameter(5, utcNow)
                .setParameter(6, utcNow)
                .executeUpdate();
        return entityManager.createQuery("""
                select count(e) from JpaInboxMessageEntity e where e.id = :generatedId
                """, Long.class)
                .setParameter("generatedId", attemptedId)
                .getSingleResult() == 1;
    }
}
