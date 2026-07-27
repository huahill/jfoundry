package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import org.jfoundry.application.inbox.InboxMessageStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/// Claims Inbox messages using PostgreSQL's conflict-safe insert syntax.
public final class PostgreSqlJpaInboxClaimStrategy implements JpaInboxClaimStrategy {

    private static final InstantUtcConverter UTC_CONVERTER = new InstantUtcConverter();

    @Override
    public boolean tryClaim(EntityManager entityManager, String messageId, String consumerName,
                            String claimToken, Instant now) {
        LocalDateTime utcNow = UTC_CONVERTER.convertToDatabaseColumn(now).toLocalDateTime();
        return entityManager.createNativeQuery("""
                insert into jfoundry_inbox_message
                    (id, message_id, consumer_name, status, claimed_at, claim_token, created_at, updated_at)
                values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
                on conflict (consumer_name, message_id) do nothing
                """)
                .setParameter(1, UUID.randomUUID().toString())
                .setParameter(2, messageId)
                .setParameter(3, consumerName)
                .setParameter(4, InboxMessageStatus.PROCESSING.name())
                .setParameter(5, utcNow)
                .setParameter(6, claimToken)
                .setParameter(7, utcNow)
                .setParameter(8, utcNow)
                .executeUpdate() == 1;
    }
}
