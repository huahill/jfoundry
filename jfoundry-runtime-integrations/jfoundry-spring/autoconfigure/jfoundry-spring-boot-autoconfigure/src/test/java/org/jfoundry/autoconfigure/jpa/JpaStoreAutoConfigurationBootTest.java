package org.jfoundry.autoconfigure.jpa;

import jakarta.persistence.EntityManagerFactory;
import org.jfoundry.application.inbox.InboxMessage;
import org.jfoundry.application.inbox.InboxExecutionResult;
import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.inbox.InboxTemplate;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageEntity;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageEntity;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JpaStoreAutoConfigurationBootTest.Application.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:jpa-store-auto-configuration;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.autoconfigure.exclude=org.jfoundry.autoconfigure.inbox.InboxMybatisPlusAutoConfiguration,org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration"
})
class JpaStoreAutoConfigurationBootTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private OutboxMessageStore outboxMessageStore;

    @Autowired
    private OutboxTemplate outboxTemplate;

    @Autowired
    private InboxMessageStore inboxMessageStore;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @Autowired
    private InboxTemplate inboxTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void bootsWithTheStandardJpaFactoryAndMapsApplicationAndFrameworkEntities() {
        assertThat(outboxMessageStore).isInstanceOf(JpaOutboxMessageStore.class);
        assertThat(outboxTemplate).isNotNull();
        assertThat(inboxMessageStore).isInstanceOf(JpaInboxMessageStore.class);
        assertThat(entityManagerFactory.getMetamodel().entity(JpaStoreApplicationEntity.class)).isNotNull();
        assertThat(entityManagerFactory.getMetamodel().entity(JpaOutboxMessageEntity.class)).isNotNull();
        assertThat(entityManagerFactory.getMetamodel().entity(JpaInboxMessageEntity.class)).isNotNull();

        transactions.executeWithoutResult(ignored -> outboxMessageStore.append(
                OutboxMessage.newPending("evt-1", "topic", null, "example.Event", "{}", Instant.now())));

        List<OutboxMessage> dispatchable = transactions.execute(
                ignored -> outboxMessageStore.findDispatchable(1, Instant.now()));
        assertThat(dispatchable)
                .extracting(OutboxMessage::getEventId)
                .containsExactly("evt-1");
    }

    @Test
    void dispatchesAndProcessesInboxMessagesThroughJpaTransactionBoundaries() {
        transactions.executeWithoutResult(ignored -> outboxMessageStore.append(
                OutboxMessage.newPending("evt-transactional", "topic", null, "example.Event", "{}", Instant.now())));

        outboxDispatcher.dispatch(10);

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(jdbcTemplate.queryForObject(
                        "select status from jfoundry_outbox_event where event_id = ?",
                        String.class,
                        "evt-transactional")).isEqualTo("PUBLISHED"));
        assertThat(inboxTemplate.executeOnce("inbox-transactional", "projection", () -> {}))
                .isEqualTo(InboxExecutionResult.PROCESSED);
        assertThat(jdbcTemplate.queryForObject(
                "select status from jfoundry_inbox_message where message_id = ? and consumer_name = ?",
                String.class, "inbox-transactional", "projection"))
                .isEqualTo("PROCESSED");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Application {

        @Bean
        JpaInboxClaimStrategy h2JpaInboxClaimStrategy() {
            return (entityManager, messageId, consumerName, claimToken, now) -> {
                InboxMessage message = InboxMessage.processing(messageId, consumerName);
                message.setClaimedAt(now);
                message.setClaimToken(claimToken);
                message.setCreatedAt(now);
                message.setUpdatedAt(now);
                entityManager.persist(JpaInboxMessageEntity.fromMessage(message));
                return true;
            };
        }

        @Bean
        MessageSender messageSender() {
            return outbound -> SendResult.ok();
        }
    }
}
