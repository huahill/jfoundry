package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Convert;
import org.jfoundry.application.inbox.InboxMessage;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaInboxMessageEntityTest {

    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-inbox-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @Test
    void persistsEveryInboxMessageFieldAndRejectsDuplicateConsumerMessagePairs() {
        Instant now = Instant.parse("2026-07-16T10:15:30Z");
        InboxMessage message = InboxMessage.processing("msg-1", "billing");
        message.setStatus(InboxMessageStatus.FAILED);
        message.setProcessedAt(now.plusSeconds(1));
        message.setClaimedAt(now);
        message.setClaimToken("d1d45133-5d66-4bd1-9674-cf1bf2e4e1bf");
        message.setCreatedAt(now.minusSeconds(2));
        message.setUpdatedAt(now.plusSeconds(2));
        message.setErrorMessage("downstream unavailable");

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.persist(JpaInboxMessageEntity.fromMessage(message));
        entityManager.getTransaction().commit();
        entityManager.clear();

        JpaInboxMessageEntity loaded = entityManager.createQuery("""
                select e from JpaInboxMessageEntity e
                 where e.messageId = :messageId and e.consumerName = :consumerName
                """, JpaInboxMessageEntity.class)
                .setParameter("messageId", "msg-1")
                .setParameter("consumerName", "billing")
                .getSingleResult();

        InboxMessage restored = loaded.toMessage();
        assertThat(restored.getMessageId()).isEqualTo("msg-1");
        assertThat(restored.getConsumerName()).isEqualTo("billing");
        assertThat(restored.getStatus()).isEqualTo(InboxMessageStatus.FAILED);
        assertThat(restored.getProcessedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(restored.getClaimedAt()).isEqualTo(now);
        assertThat(restored.getClaimToken()).isEqualTo("d1d45133-5d66-4bd1-9674-cf1bf2e4e1bf");
        assertThat(restored.getCreatedAt()).isEqualTo(now.minusSeconds(2));
        assertThat(restored.getUpdatedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(restored.getErrorMessage()).isEqualTo("downstream unavailable");

        entityManager.getTransaction().begin();
        entityManager.persist(JpaInboxMessageEntity.fromMessage(message));
        assertThatThrownBy(() -> entityManager.getTransaction().commit())
                .hasRootCauseInstanceOf(java.sql.SQLIntegrityConstraintViolationException.class);
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
        entityManager.close();
    }

    @Test
    void mapsTimestampColumnsWithTheExplicitUtcConverter() throws NoSuchFieldException {
        assertThat(converter("processedAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("claimedAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("createdAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("updatedAt")).isEqualTo(InstantUtcConverter.class);
    }

    private static Class<?> converter(String fieldName) throws NoSuchFieldException {
        return JpaInboxMessageEntity.class.getDeclaredField(fieldName)
                .getAnnotation(Convert.class)
                .converter();
    }
}
