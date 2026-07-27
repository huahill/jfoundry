package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;

import java.time.Instant;

/// Atomically claims a previously unseen Inbox message for a consumer.
public interface JpaInboxClaimStrategy {

    boolean tryClaim(EntityManager entityManager, String messageId, String consumerName,
                     String claimToken, Instant now);
}
