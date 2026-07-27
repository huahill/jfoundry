package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimResult;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxExecutionResult;
import org.jfoundry.application.inbox.InboxMessage;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JpaInboxMessageStoreTest {

    private static final Duration LEASE = Duration.ofMinutes(5);
    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private AtomicInteger claimAttempts;
    private JpaInboxMessageStore store;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-inbox-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        claimAttempts = new AtomicInteger();
        store = new JpaInboxMessageStore(entityManager, (manager, messageId, consumerName, claimToken, now) -> {
            claimAttempts.incrementAndGet();
            InboxMessage message = InboxMessage.processing(messageId, consumerName);
            message.setClaimedAt(now);
            message.setClaimToken(claimToken);
            message.setCreatedAt(now);
            message.setUpdatedAt(now);
            manager.persist(JpaInboxMessageEntity.fromMessage(message));
            return true;
        });
        inTransaction(() -> entityManager.createQuery("delete from JpaInboxMessageEntity").executeUpdate());
    }

    @AfterEach
    void closeEntityManager() {
        entityManager.close();
    }

    @Test
    void claimsAnAbsentMessageAndCompletesItOnlyForTheOwningToken() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");

        InboxClaim claim = inTransactionResult(() -> store.claim("msg-1", "billing", now, LEASE));

        assertThat(claim.result()).isEqualTo(InboxClaimResult.CLAIMED);
        assertThat(claim.source()).isEqualTo(InboxClaimSource.FRESH);
        assertThat(claimAttempts).hasValue(1);
        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "billing", "stale", now))).isFalse();
        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "billing", claim.claimToken(), now))).isTrue();
        assertThat(inTransactionResult(() -> store.claim("msg-1", "billing", now, LEASE).result()))
                .isEqualTo(InboxClaimResult.DUPLICATE);
    }

    @Test
    void retriesFailedMessagesWithANewOwnershipToken() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim initial = inTransactionResult(() -> store.claim("msg-1", "billing", now, LEASE));
        assertThat(inTransactionResult(() -> store.markFailed(
                "msg-1", "billing", initial.claimToken(), "temporary failure", now))).isTrue();

        InboxClaim retry = inTransactionResult(() -> store.claim("msg-1", "billing", now.plusSeconds(1), LEASE));

        assertThat(retry.source()).isEqualTo(InboxClaimSource.FAILED_RETRY);
        assertThat(retry.claimToken()).isNotEqualTo(initial.claimToken());
        assertThat(load("msg-1", "billing").getErrorMessage()).isNull();
    }

    @Test
    void reclaimsExpiredLeasesAndRejectsStaleCompletion() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim initial = inTransactionResult(() -> store.claim("msg-1", "billing", now, LEASE));

        InboxClaim replacement = inTransactionResult(() -> store.claim(
                "msg-1", "billing", now.plus(LEASE).plusSeconds(1), LEASE));

        assertThat(replacement.source()).isEqualTo(InboxClaimSource.EXPIRED_LEASE);
        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "billing", initial.claimToken(), now))).isFalse();
        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "billing", replacement.claimToken(), now))).isTrue();
    }

    @Test
    void returnsInProgressForAnActiveLease() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        inTransaction(() -> store.claim("msg-1", "billing", now, LEASE));

        InboxClaim duplicateClaim = inTransactionResult(() -> store.claim("msg-1", "billing", now.plusSeconds(1), LEASE));

        assertThat(duplicateClaim.result()).isEqualTo(InboxClaimResult.IN_PROGRESS);
    }

    @Test
    void allowsDifferentConsumersToProcessTheSameMessageIndependently() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim billing = inTransactionResult(() -> store.claim("msg-1", "billing", now, LEASE));
        InboxClaim analytics = inTransactionResult(() -> store.claim("msg-1", "analytics", now, LEASE));

        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "billing", billing.claimToken(), now))).isTrue();
        assertThat(inTransactionResult(() -> store.markProcessed("msg-1", "analytics", analytics.claimToken(), now))).isTrue();
        assertThat(count("msg-1")).isEqualTo(2);
    }

    @Test
    void doesNotInvokeTheAbsentRowStrategyForProcessedMessages() {
        persist(InboxMessage.processed("msg-1", "billing"));

        InboxClaim claim = inTransactionResult(() -> store.claim("msg-1", "billing", Instant.now(), LEASE));

        assertThat(claim.result()).isEqualTo(InboxClaimResult.DUPLICATE);
        assertThat(claimAttempts).hasValue(0);
    }

    private void persist(InboxMessage message) {
        inTransaction(() -> entityManager.persist(JpaInboxMessageEntity.fromMessage(message)));
    }

    private InboxMessage load(String messageId, String consumerName) {
        return inTransactionResult(() -> {
            entityManager.clear();
            return entityManager.createQuery("""
                    select e from JpaInboxMessageEntity e
                     where e.messageId = :messageId and e.consumerName = :consumerName
                    """, JpaInboxMessageEntity.class)
                    .setParameter("messageId", messageId)
                    .setParameter("consumerName", consumerName)
                    .getSingleResult()
                    .toMessage();
        });
    }

    private long count(String messageId) {
        return inTransactionResult(() -> entityManager.createQuery("""
                select count(e) from JpaInboxMessageEntity e where e.messageId = :messageId
                """, Long.class).setParameter("messageId", messageId).getSingleResult());
    }

    private void inTransaction(Runnable work) {
        entityManager.getTransaction().begin();
        try {
            work.run();
            entityManager.getTransaction().commit();
        } catch (RuntimeException exception) {
            entityManager.getTransaction().rollback();
            throw exception;
        }
    }

    private <T> T inTransactionResult(java.util.function.Supplier<T> work) {
        entityManager.getTransaction().begin();
        try {
            T result = work.get();
            entityManager.getTransaction().commit();
            return result;
        } catch (RuntimeException exception) {
            entityManager.getTransaction().rollback();
            throw exception;
        }
    }
}
