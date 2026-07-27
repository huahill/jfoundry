package org.jfoundry.infrastructure.outbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Lob;
import jakarta.persistence.Persistence;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JpaOutboxMessageEntityTest {

    private static EntityManagerFactory entityManagerFactory;

    @BeforeAll
    static void createEntityManagerFactory() {
        entityManagerFactory = Persistence.createEntityManagerFactory("jfoundry-outbox-jpa-test");
    }

    @AfterAll
    static void closeEntityManagerFactory() {
        entityManagerFactory.close();
    }

    @Test
    void persistsEveryOutboxMessageFieldWithTheDocumentedTableMapping() {
        Instant now = Instant.parse("2026-07-16T10:15:30Z");
        OutboxMessage message = OutboxMessage.newPending(
                "evt-1", "orders", "order-1", "example.OrderPlaced", "{\"id\":1}", now,
                "Order", "order-1", 4L);
        message.setStatus(OutboxMessageStatus.FAILED);
        message.setRetryCount(2);
        message.setErrorMessage("broker unavailable");
        message.setLastAttemptAt(now.plusSeconds(1));
        message.setNextRetryAt(now.plusSeconds(10));
        message.setCreatedAt(now.minusSeconds(2));
        message.setUpdatedAt(now.plusSeconds(2));
        message.setClaimedAt(now.plusSeconds(3));
        message.setClaimedBy("node-a");
        message.setClaimToken("token-1");
        message.setPropagation(MessagePropagation.from(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "tracestate", "vendor=value")));

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        entityManager.persist(JpaOutboxMessageEntity.fromMessage(message));
        entityManager.getTransaction().commit();
        entityManager.clear();

        OutboxMessage loaded = entityManager.find(JpaOutboxMessageEntity.class, "evt-1").toMessage();

        assertThat(loaded.getEventId()).isEqualTo("evt-1");
        assertThat(loaded.getTopic()).isEqualTo("orders");
        assertThat(loaded.getPayloadKey()).isEqualTo("order-1");
        assertThat(loaded.getPayloadType()).isEqualTo("example.OrderPlaced");
        assertThat(loaded.getPayloadJson()).isEqualTo("{\"id\":1}");
        assertThat(loaded.getAggregateType()).isEqualTo("Order");
        assertThat(loaded.getAggregateId()).isEqualTo("order-1");
        assertThat(loaded.getAggregateVersion()).isEqualTo(4L);
        assertThat(loaded.getStatus()).isEqualTo(OutboxMessageStatus.FAILED);
        assertThat(loaded.getRetryCount()).isEqualTo(2);
        assertThat(loaded.getErrorMessage()).isEqualTo("broker unavailable");
        assertThat(loaded.getOccurredAt()).isEqualTo(now);
        assertThat(loaded.getLastAttemptAt()).isEqualTo(now.plusSeconds(1));
        assertThat(loaded.getNextRetryAt()).isEqualTo(now.plusSeconds(10));
        assertThat(loaded.getCreatedAt()).isEqualTo(now.minusSeconds(2));
        assertThat(loaded.getUpdatedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(loaded.getClaimedAt()).isEqualTo(now.plusSeconds(3));
        assertThat(loaded.getClaimedBy()).isEqualTo("node-a");
        assertThat(loaded.getClaimToken()).isEqualTo("token-1");
        assertThat(loaded.getPropagation()).isEqualTo(message.getPropagation());
        entityManager.close();
    }

    @Test
    void mapsPayloadAndInstantsToThePortableSqlTemplateColumnTypes() throws NoSuchFieldException {
        assertThat(JpaOutboxMessageEntity.class.getDeclaredField("payloadJson")
                .isAnnotationPresent(Lob.class)).isFalse();
        assertThat(columnDefinition("payloadJson")).isEqualToIgnoringCase("text");
        assertThat(columnDefinition("occurredAt")).isEqualToIgnoringCase("timestamp");
        assertThat(columnDefinition("lastAttemptAt")).isEqualToIgnoringCase("timestamp");
        assertThat(columnDefinition("nextRetryAt")).isEqualToIgnoringCase("timestamp");
        assertThat(columnDefinition("createdAt")).isEqualToIgnoringCase("timestamp");
        assertThat(columnDefinition("updatedAt")).isEqualToIgnoringCase("timestamp");
        assertThat(columnDefinition("claimedAt")).isEqualToIgnoringCase("timestamp");
        assertThat(converter("occurredAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("lastAttemptAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("nextRetryAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("createdAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("updatedAt")).isEqualTo(InstantUtcConverter.class);
        assertThat(converter("claimedAt")).isEqualTo(InstantUtcConverter.class);
    }

    private static String columnDefinition(String fieldName) throws NoSuchFieldException {
        return JpaOutboxMessageEntity.class.getDeclaredField(fieldName)
                .getAnnotation(Column.class)
                .columnDefinition();
    }

    private static Class<?> converter(String fieldName) throws NoSuchFieldException {
        return JpaOutboxMessageEntity.class.getDeclaredField(fieldName)
                .getAnnotation(Convert.class)
                .converter();
    }
}
