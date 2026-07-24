package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Persistence;
import org.jfoundry.application.inbox.InboxMessage;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JpaInboxMessageStoreTest {

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
        store = new JpaInboxMessageStore(entityManager, (manager, messageId, consumerName, now) -> {
            claimAttempts.incrementAndGet();
            manager.persist(JpaInboxMessageEntity.fromMessage(InboxMessage.processing(messageId, consumerName)));
            return true;
        });
        inTransaction(() -> {
            entityManager.createQuery("delete from JpaInboxMessageEntity").executeUpdate();
            entityManager.createQuery("delete from UnrelatedTestEntity").executeUpdate();
        });
    }

    @AfterEach
    void closeEntityManager() {
        entityManager.close();
    }

    @Test
    void retriesFailedMessagesWithACasTransitionToProcessing() {
        persist(failed("msg-1", "billing", "temporary failure"));

        boolean started = inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"));

        assertThat(started).isTrue();
        assertThat(claimAttempts).hasValue(0);
        InboxMessage message = load("msg-1", "billing");
        assertThat(message.getStatus()).isEqualTo(InboxMessageStatus.PROCESSING);
        assertThat(message.getErrorMessage()).isNull();
    }

    @Test
    void doesNotReclaimProcessedMessagesOrInvokeTheAbsentRowStrategy() {
        persist(InboxMessage.processed("msg-1", "billing"));

        boolean started = inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"));

        assertThat(started).isFalse();
        assertThat(claimAttempts).hasValue(0);
        assertThat(store.isProcessed("msg-1", "billing")).isTrue();
    }

    @Test
    void invokesTheInjectedClaimStrategyForAnAbsentMessage() {
        boolean started = inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"));

        assertThat(started).isTrue();
        assertThat(claimAttempts).hasValue(1);
        assertThat(load("msg-1", "billing").getStatus()).isEqualTo(InboxMessageStatus.PROCESSING);
    }

    @Test
    void allowsDifferentConsumersToProcessTheSameMessageIndependently() {
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"))).isTrue();
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "analytics"))).isTrue();
        inTransaction(() -> store.markProcessed("msg-1", "billing"));
        inTransaction(() -> store.markProcessed("msg-1", "analytics"));

        assertThat(store.isProcessed("msg-1", "billing")).isTrue();
        assertThat(store.isProcessed("msg-1", "analytics")).isTrue();
        assertThat(count("msg-1")).isEqualTo(2);
    }

    @Test
    void staleCompletionCannotOverwriteAFailedMessage() {
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"))).isTrue();
        inTransaction(() -> store.markFailed("msg-1", "billing", "temporary failure"));
        inTransaction(() -> store.markProcessed("msg-1", "billing"));

        InboxMessage message = load("msg-1", "billing");
        assertThat(message.getStatus()).isEqualTo(InboxMessageStatus.FAILED);
        assertThat(message.getErrorMessage()).isEqualTo("temporary failure");
    }

    @Test
    void staleFailureCannotOverwriteAProcessedMessage() {
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"))).isTrue();
        inTransaction(() -> store.markProcessed("msg-1", "billing"));
        inTransaction(() -> store.markFailed("msg-1", "billing", "stale failure"));

        InboxMessage message = load("msg-1", "billing");
        assertThat(message.getStatus()).isEqualTo(InboxMessageStatus.PROCESSED);
        assertThat(message.getErrorMessage()).isNull();
        assertThat(store.isProcessed("msg-1", "billing")).isTrue();
    }

    @Test
    void retryFlushesUnrelatedChangesBeforeClearingThePersistenceContext() {
        persist(failed("msg-1", "billing", "temporary failure"));
        persistUnrelatedEntity();

        mutateUnrelatedEntityAndRun(() -> assertThat(store.tryStartProcessing("msg-1", "billing")).isTrue());

        assertThat(loadUnrelatedEntity().getDescription()).isEqualTo("after");
    }

    @Test
    void markProcessedFlushesUnrelatedChangesBeforeClearingThePersistenceContext() {
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"))).isTrue();
        persistUnrelatedEntity();

        mutateUnrelatedEntityAndRun(() -> store.markProcessed("msg-1", "billing"));

        assertThat(loadUnrelatedEntity().getDescription()).isEqualTo("after");
    }

    @Test
    void markFailedFlushesUnrelatedChangesBeforeClearingThePersistenceContext() {
        assertThat(inTransactionResult(() -> store.tryStartProcessing("msg-1", "billing"))).isTrue();
        persistUnrelatedEntity();

        mutateUnrelatedEntityAndRun(() -> store.markFailed("msg-1", "billing", "temporary failure"));

        assertThat(loadUnrelatedEntity().getDescription()).isEqualTo("after");
    }

    @Test
    void usesJpqlForPortableStateTransitions() {
        JpaInboxMessageStore portableStore = new JpaInboxMessageStore(
                entityManagerRejectingNativeQueries(entityManager),
                (manager, messageId, consumerName, now) -> false);
        persist(failed("retry", "billing", "temporary failure"));
        persist(InboxMessage.processing("failure", "billing"));

        inTransaction(() -> portableStore.tryStartProcessing("retry", "billing"));
        inTransaction(() -> portableStore.markProcessed("retry", "billing"));
        inTransaction(() -> portableStore.markFailed("failure", "billing", "temporary failure"));
    }

    private void persist(InboxMessage message) {
        inTransaction(() -> entityManager.persist(JpaInboxMessageEntity.fromMessage(message)));
    }

    private void persistUnrelatedEntity() {
        inTransaction(() -> entityManager.persist(new UnrelatedTestEntity("unrelated-1", "before")));
    }

    private void mutateUnrelatedEntityAndRun(Runnable transition) {
        inTransaction(() -> {
            entityManager.setFlushMode(FlushModeType.COMMIT);
            UnrelatedTestEntity unrelated = entityManager.find(UnrelatedTestEntity.class, "unrelated-1");
            unrelated.setDescription("after");
            transition.run();
        });
    }

    private UnrelatedTestEntity loadUnrelatedEntity() {
        return inTransactionResult(() -> {
            entityManager.clear();
            return entityManager.find(UnrelatedTestEntity.class, "unrelated-1");
        });
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
                """, Long.class)
                .setParameter("messageId", messageId)
                .getSingleResult());
    }

    private static InboxMessage failed(String messageId, String consumerName, String errorMessage) {
        InboxMessage message = InboxMessage.processing(messageId, consumerName);
        message.setStatus(InboxMessageStatus.FAILED);
        message.setErrorMessage(errorMessage);
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

    private static EntityManager entityManagerRejectingNativeQueries(EntityManager delegate) {
        return (EntityManager) Proxy.newProxyInstance(
                JpaInboxMessageStoreTest.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                (proxy, method, arguments) -> {
                    if ("createNativeQuery".equals(method.getName())) {
                        throw new AssertionError("Portable Inbox state transitions must use JPQL");
                    }
                    try {
                        return method.invoke(delegate, arguments);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }
}
