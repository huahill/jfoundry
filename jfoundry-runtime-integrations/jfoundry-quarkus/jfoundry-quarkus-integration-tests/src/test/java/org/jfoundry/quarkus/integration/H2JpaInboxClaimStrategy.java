package org.jfoundry.quarkus.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@ApplicationScoped
class H2JpaInboxClaimStrategy implements JpaInboxClaimStrategy {

    @Override
    public boolean tryClaim(EntityManager entityManager, String messageId, String consumerName, Instant now) {
        String attemptedId = UUID.randomUUID().toString();
        LocalDateTime utcNow = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        entityManager.createNativeQuery("""
                merge into jfoundry_inbox_message
                    (id, message_id, consumer_name, status, created_at, updated_at)
                key (consumer_name, message_id)
                values (coalesce((select id from jfoundry_inbox_message
                                  where consumer_name = ?3 and message_id = ?2), ?1), ?2, ?3, ?4, ?5, ?6)
                """)
                .setParameter(1, attemptedId)
                .setParameter(2, messageId)
                .setParameter(3, consumerName)
                .setParameter(4, InboxMessageStatus.PROCESSING.name())
                .setParameter(5, utcNow)
                .setParameter(6, utcNow)
                .executeUpdate();
        return entityManager.createQuery("""
                select count(e) from JpaInboxMessageEntity e where e.id = :attemptedId
                """, Long.class)
                .setParameter("attemptedId", attemptedId)
                .getSingleResult() == 1;
    }
}
