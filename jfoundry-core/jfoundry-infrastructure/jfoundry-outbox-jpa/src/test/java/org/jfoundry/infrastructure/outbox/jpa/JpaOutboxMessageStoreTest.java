package org.jfoundry.infrastructure.outbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaOutboxMessageStoreTest {

    private static EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;
    private JpaOutboxMessageStore store;

    private final BackoffStrategy fixedBackoff = failedAttempts -> Duration.ofSeconds(10);

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-outbox-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @BeforeEach
    void setUp() {
        entityManager = entityManagerFactory.createEntityManager();
        store = new JpaOutboxMessageStore(entityManager);
        inTransaction(() -> entityManager.createQuery("delete from JpaOutboxMessageEntity").executeUpdate());
    }

    @AfterEach
    void closeEntityManager() {
        entityManager.close();
    }

    @Test
    void claimReturnsOnlyRowsWhoseConditionalUpdateSucceededInDispatchOrder() {
        append(pending("evt-2", Instant.parse("2026-07-16T10:00:01Z")));
        append(pending("evt-1", Instant.parse("2026-07-16T10:00:00Z")));
        OutboxMessage futureRetry = pending("evt-future", Instant.parse("2026-07-16T09:59:00Z"));
        futureRetry.setStatus(OutboxMessageStatus.FAILED);
        futureRetry.setNextRetryAt(Instant.now().plusSeconds(60));
        append(futureRetry);

        List<OutboxMessage> claimed = inTransactionResult(() -> store.claimDispatchable(2, "node-a"));

        assertThat(claimed).extracting(OutboxMessage::getEventId).containsExactly("evt-1", "evt-2");
        assertThat(claimed).allSatisfy(message -> {
            assertThat(message.getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);
            assertThat(message.getClaimedBy()).isEqualTo("node-a");
            assertThat(message.getClaimedAt()).isNotNull();
            assertThat(message.getClaimToken()).isNotBlank();
        });
        assertThat(claimed).extracting(OutboxMessage::getClaimToken).containsOnly(claimed.get(0).getClaimToken());
        assertThat(load("evt-future").getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
    }

    @Test
    void findDispatchableOrdersPendingAndRetryDueRowsByOccurrenceThenEventId() {
        Instant occurredAt = Instant.parse("2026-07-16T10:00:00Z");
        append(pending("evt-b", occurredAt));
        append(pending("evt-a", occurredAt));
        OutboxMessage failedDue = pending("evt-retry", occurredAt.plusSeconds(1));
        failedDue.setStatus(OutboxMessageStatus.FAILED);
        failedDue.setNextRetryAt(Instant.now().minusSeconds(1));
        append(failedDue);

        List<OutboxMessage> dispatchable = inTransactionResult(() -> store.findDispatchable(3, Instant.now()));

        assertThat(dispatchable).extracting(OutboxMessage::getEventId)
                .containsExactly("evt-a", "evt-b", "evt-retry");
    }

    @Test
    void staleClaimTokenCannotPublishOrFailAClaimedRow() {
        append(pending("evt-1", Instant.now()));
        OutboxMessage claim = inTransactionResult(() -> store.claimDispatchable(1, "node-a").get(0));

        inTransaction(() -> store.markAsPublished("evt-1", "wrong-token"));
        assertThat(load("evt-1").getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);
        inTransaction(() -> store.markAsFailed("evt-1", "wrong-token", "boom", 3, fixedBackoff));
        assertThat(load("evt-1").getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);

        inTransaction(() -> store.markAsPublished("evt-1", claim.getClaimToken()));
        OutboxMessage published = load("evt-1");
        assertThat(published.getStatus()).isEqualTo(OutboxMessageStatus.PUBLISHED);
        assertThat(published.getClaimedAt()).isNull();
        assertThat(published.getClaimedBy()).isNull();
        assertThat(published.getClaimToken()).isNull();
    }

    @Test
    void failureRetryDeadLetterAndReactivationFollowTheCoreStateMachine() {
        append(pending("evt-1", Instant.now()));
        OutboxMessage firstClaim = inTransactionResult(() -> store.claimDispatchable(1, "node-a").get(0));

        inTransaction(() -> store.markAsFailed("evt-1", firstClaim.getClaimToken(), "boom", 2, fixedBackoff));
        OutboxMessage failed = load("evt-1");
        assertThat(failed.getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isAfter(failed.getLastAttemptAt());
        assertThat(failed.getClaimToken()).isNull();

        inTransaction(() -> entityManager.createQuery("update JpaOutboxMessageEntity e set e.nextRetryAt = :now")
                .setParameter("now", Instant.now().minusSeconds(1)).executeUpdate());
        OutboxMessage secondClaim = inTransactionResult(() -> store.claimDispatchable(1, "node-b").get(0));
        inTransaction(() -> store.markAsFailed("evt-1", secondClaim.getClaimToken(), "boom again", 2, fixedBackoff));
        assertThat(load("evt-1").getStatus()).isEqualTo(OutboxMessageStatus.DEAD_LETTERED);

        inTransaction(() -> store.reactivate("evt-1"));
        OutboxMessage reactivated = load("evt-1");
        assertThat(reactivated.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(reactivated.getRetryCount()).isZero();
        assertThat(reactivated.getErrorMessage()).isNull();
    }

    @Test
    void recoveryClearsOnlyOwnershipOlderThanTheCutoff() {
        append(pending("old", Instant.now()));
        append(pending("fresh", Instant.now()));
        inTransaction(() -> store.claimDispatchable(2, "node-a"));
        inTransaction(() -> entityManager.createQuery("update JpaOutboxMessageEntity e set e.claimedAt = :old where e.eventId = :eventId")
                .setParameter("old", Instant.now().minusSeconds(120))
                .setParameter("eventId", "old").executeUpdate());

        int recovered = inTransactionResult(() -> store.recoverStuckDispatching(Instant.now().minusSeconds(60)));

        assertThat(recovered).isEqualTo(1);
        OutboxMessage old = load("old");
        assertThat(old.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(old.getClaimedAt()).isNull();
        assertThat(old.getClaimedBy()).isNull();
        assertThat(old.getClaimToken()).isNull();
        assertThat(load("fresh").getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);
    }

    @Test
    void cleanupDeletesBoundedPagesForBothTerminalStatuses() {
        Instant old = Instant.now().minusSeconds(120);
        append(terminal("published-1", OutboxMessageStatus.PUBLISHED, old));
        append(terminal("published-2", OutboxMessageStatus.PUBLISHED, old));
        append(terminal("dead-1", OutboxMessageStatus.DEAD_LETTERED, old));
        append(terminal("recent", OutboxMessageStatus.PUBLISHED, Instant.now()));

        assertThat(inTransactionResult(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(60), 1))).isEqualTo(2);
        assertThat(inTransactionResult(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.DEAD_LETTERED, Instant.now().minusSeconds(60), 1))).isEqualTo(1);
        assertThat(load("published-1")).isNull();
        assertThat(load("published-2")).isNull();
        assertThat(load("dead-1")).isNull();
        assertThat(load("recent")).isNotNull();
    }

    @Test
    void cleanupRejectsNonTerminalStatusesAndLeavesTheirRowsIntact() {
        Instant old = Instant.now().minusSeconds(120);
        append(pending("pending", old));
        OutboxMessage failed = pending("failed", old);
        failed.setStatus(OutboxMessageStatus.FAILED);
        append(failed);

        assertThatThrownBy(() -> inTransaction(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PENDING, Instant.now(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal");
        assertThatThrownBy(() -> inTransaction(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.FAILED, Instant.now(), 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal");

        assertThat(load("pending")).isNotNull();
        assertThat(load("failed")).isNotNull();
    }

    @Test
    void cleanupDoesNotDeleteRowsWhoseStatusOrOccurrenceChangesAfterTheirIdsWereSelected() {
        Instant old = Instant.now().minusSeconds(120);
        append(terminal("reactivated", OutboxMessageStatus.PUBLISHED, old));
        append(terminal("retained", OutboxMessageStatus.PUBLISHED, old));
        EntityManager reactivatingManager = entityManagerFactory.createEntityManager();
        EntityManager cleanupManager = entityManagerFactory.createEntityManager();
        try {
            JpaOutboxMessageStore cleanupStore = new JpaOutboxMessageStore(
                    entityManagerThatRunsBeforeBulkUpdate(cleanupManager, "delete from JpaOutboxMessageEntity", () ->
                            inTransaction(reactivatingManager, () -> {
                                reactivatingManager.createQuery("""
                                        update JpaOutboxMessageEntity e
                                           set e.status = :pending
                                         where e.eventId = :eventId
                                        """)
                                        .setParameter("pending", OutboxMessageStatus.PENDING.name())
                                        .setParameter("eventId", "reactivated")
                                        .executeUpdate();
                                reactivatingManager.createQuery("""
                                        update JpaOutboxMessageEntity e
                                           set e.occurredAt = :occurredAt
                                         where e.eventId = :eventId
                                        """)
                                        .setParameter("occurredAt", Instant.now())
                                        .setParameter("eventId", "retained")
                                        .executeUpdate();
                            })));

            int deleted = inTransactionResult(cleanupManager, () -> cleanupStore.deleteByStatusAndOccurredAtBefore(
                    OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(60), 2));

            assertThat(deleted).isZero();
            assertThat(load("reactivated").getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
            assertThat(load("retained")).isNotNull();
        } finally {
            reactivatingManager.close();
            cleanupManager.close();
        }
    }

    @Test
    void cleanupContinuesAfterAGuardedPageDeleteLosesEveryCandidate() {
        Instant old = Instant.now().minusSeconds(120);
        append(terminal("cleanup-1", OutboxMessageStatus.PUBLISHED, old));
        append(terminal("cleanup-2", OutboxMessageStatus.PUBLISHED, old));
        append(terminal("cleanup-3", OutboxMessageStatus.PUBLISHED, old));
        EntityManager competingManager = entityManagerFactory.createEntityManager();
        EntityManager cleanupManager = entityManagerFactory.createEntityManager();
        try {
            JpaOutboxMessageStore cleanupStore = new JpaOutboxMessageStore(
                    entityManagerThatRunsBeforeBulkUpdate(cleanupManager, "delete from JpaOutboxMessageEntity", () ->
                            inTransaction(competingManager, () -> competingManager.createQuery("""
                                    update JpaOutboxMessageEntity e
                                       set e.status = :pending
                                     where e.eventId in :eventIds
                                    """)
                                    .setParameter("pending", OutboxMessageStatus.PENDING.name())
                                    .setParameter("eventIds", List.of("cleanup-1", "cleanup-2"))
                                    .executeUpdate())));

            int deleted = inTransactionResult(cleanupManager, () -> cleanupStore.deleteByStatusAndOccurredAtBefore(
                    OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(60), 2));

            assertThat(deleted).isEqualTo(1);
            assertThat(load("cleanup-1").getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
            assertThat(load("cleanup-2").getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
            assertThat(load("cleanup-3")).isNull();
        } finally {
            competingManager.close();
            cleanupManager.close();
        }
    }

    @Test
    void losingClaimCasIsExcludedWhenAnotherTransactionClaimsTheCandidateFirst() {
        append(pending("contested", Instant.now()));
        EntityManager winnerManager = entityManagerFactory.createEntityManager();
        EntityManager loserManager = entityManagerFactory.createEntityManager();
        try {
            JpaOutboxMessageStore winnerStore = new JpaOutboxMessageStore(winnerManager);
            JpaOutboxMessageStore loserStore = new JpaOutboxMessageStore(
                    entityManagerThatRunsBeforeBulkUpdate(loserManager, "e.status = :candidateStatus", () ->
                            inTransaction(winnerManager, () -> winnerStore.claimDispatchable(1, "winner"))));

            List<OutboxMessage> claimed = inTransactionResult(loserManager, () -> loserStore.claimDispatchable(1, "loser"));

            assertThat(claimed).isEmpty();
            assertThat(load("contested").getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);
            assertThat(load("contested").getClaimedBy()).isEqualTo("winner");
        } finally {
            winnerManager.close();
            loserManager.close();
        }
    }

    @Test
    void losingClaimCasRetriesWithTheNextDispatchableCandidate() {
        Instant occurredAt = Instant.now();
        append(pending("first", occurredAt));
        append(pending("second", occurredAt.plusSeconds(1)));
        EntityManager winnerManager = entityManagerFactory.createEntityManager();
        EntityManager loserManager = entityManagerFactory.createEntityManager();
        try {
            JpaOutboxMessageStore winnerStore = new JpaOutboxMessageStore(winnerManager);
            AtomicBoolean winnerClaimed = new AtomicBoolean();
            JpaOutboxMessageStore loserStore = new JpaOutboxMessageStore(
                    entityManagerThatRunsBeforeBulkUpdate(loserManager, "e.status = :candidateStatus", () -> {
                        if (winnerClaimed.compareAndSet(false, true)) {
                            inTransaction(winnerManager, () -> winnerStore.claimDispatchable(1, "winner"));
                        }
                    }));

            List<OutboxMessage> claimed = inTransactionResult(loserManager, () -> loserStore.claimDispatchable(1, "loser"));

            assertThat(claimed).extracting(OutboxMessage::getEventId).containsExactly("second");
            assertThat(load("first").getClaimedBy()).isEqualTo("winner");
            assertThat(load("second").getClaimedBy()).isEqualTo("loser");
        } finally {
            winnerManager.close();
            loserManager.close();
        }
    }

    @Test
    void validatesClaimRecoveryAndCleanupInputs() {
        assertThatThrownBy(() -> store.claimDispatchable(0, "node-a"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
        assertThatThrownBy(() -> store.claimDispatchable(1, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("claimerId");
        assertThatThrownBy(() -> store.recoverStuckDispatching(null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cutoff");
        assertThatThrownBy(() -> store.deleteByStatusAndOccurredAtBefore(null, Instant.now(), 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status");
        assertThatThrownBy(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, null, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cutoff");
        assertThatThrownBy(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, Instant.now(), 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("batchSize");
    }

    private void append(OutboxMessage message) {
        inTransaction(() -> store.append(message));
    }

    private OutboxMessage load(String eventId) {
        return inTransactionResult(() -> {
            entityManager.clear();
            JpaOutboxMessageEntity entity = entityManager.find(JpaOutboxMessageEntity.class, eventId);
            return entity == null ? null : entity.toMessage();
        });
    }

    private static OutboxMessage pending(String eventId, Instant occurredAt) {
        return OutboxMessage.newPending(eventId, "topic", null, "example.Event", "{}", occurredAt);
    }

    private static OutboxMessage terminal(String eventId, OutboxMessageStatus status, Instant occurredAt) {
        OutboxMessage message = pending(eventId, occurredAt);
        message.setStatus(status);
        return message;
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
        return inTransactionResult(entityManager, work);
    }

    private static void inTransaction(EntityManager manager, Runnable work) {
        manager.getTransaction().begin();
        try {
            work.run();
            manager.getTransaction().commit();
        } catch (RuntimeException exception) {
            manager.getTransaction().rollback();
            throw exception;
        }
    }

    private static <T> T inTransactionResult(EntityManager manager, java.util.function.Supplier<T> work) {
        manager.getTransaction().begin();
        try {
            T result = work.get();
            manager.getTransaction().commit();
            return result;
        } catch (RuntimeException exception) {
            manager.getTransaction().rollback();
            throw exception;
        }
    }

    private static EntityManager entityManagerThatRunsBeforeBulkUpdate(
            EntityManager delegate, String queryFragment, Runnable beforeBulkUpdate) {
        return (EntityManager) Proxy.newProxyInstance(
                JpaOutboxMessageStoreTest.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, arguments) -> {
                    Object result = invoke(delegate, method, arguments);
                    if ("createQuery".equals(method.getName()) && arguments != null
                            && arguments.length > 0 && arguments[0] instanceof String query
                            && query.contains(queryFragment)) {
                        return queryThatRunsBeforeBulkUpdate((Query) result, beforeBulkUpdate);
                    }
                    return result;
                });
    }

    private static Query queryThatRunsBeforeBulkUpdate(Query delegate, Runnable beforeBulkUpdate) {
        AtomicBoolean invoked = new AtomicBoolean();
        return (Query) Proxy.newProxyInstance(
                JpaOutboxMessageStoreTest.class.getClassLoader(),
                new Class<?>[]{Query.class},
                (proxy, method, arguments) -> {
                    if ("executeUpdate".equals(method.getName()) && invoked.compareAndSet(false, true)) {
                        beforeBulkUpdate.run();
                    }
                    Object result = invoke(delegate, method, arguments);
                    return Query.class.isAssignableFrom(method.getReturnType()) ? proxy : result;
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
