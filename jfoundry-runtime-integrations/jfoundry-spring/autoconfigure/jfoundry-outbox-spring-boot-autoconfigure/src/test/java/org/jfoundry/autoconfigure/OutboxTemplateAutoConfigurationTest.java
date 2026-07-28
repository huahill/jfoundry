package org.jfoundry.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.autoconfigure.event.DomainEventOutboxRecorderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxTemplateAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DomainEventOutboxRecorderAutoConfiguration.class));

    @Test
    void createsTemplateWhenStoreAndSerializerExist() {
        runner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(OutboxMessageStore.class, StubOutboxMessageStore::new)
                .run(context -> assertThat(context).hasSingleBean(OutboxTemplate.class));
    }

    @Test
    void createsTemplateWithBootConfiguredObjectMapper() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DomainEventOutboxRecorderAutoConfiguration.class,
                        JacksonAutoConfiguration.class))
                .withBean(OutboxMessageStore.class, StubOutboxMessageStore::new)
                .run(context -> assertThat(context).hasSingleBean(OutboxTemplate.class));
    }

    @Test
    void backsOffWhenUserProvidesTemplate() {
        StubOutboxMessageStore store = new StubOutboxMessageStore();
        PayloadSerializer serializer = payload -> "{}";
        OutboxTemplate custom = new OutboxTemplate(store, serializer);

        runner.withBean(OutboxMessageStore.class, () -> store)
                .withBean(PayloadSerializer.class, () -> serializer)
                .withBean(OutboxTemplate.class, () -> custom)
                .run(context -> assertThat(context.getBean(OutboxTemplate.class)).isSameAs(custom));
    }

    @Test
    void doesNotCreateTemplateWithoutStore() {
        runner.withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxTemplate.class));
    }

    @Test
    void doesNotCreateTemplateWithoutSerializer() {
        runner.withBean(OutboxMessageStore.class, StubOutboxMessageStore::new)
                .run(context -> assertThat(context).doesNotHaveBean(OutboxTemplate.class));
    }

    private static final class StubOutboxMessageStore implements OutboxMessageStore {

        @Override
        public void append(OutboxMessage entry) {
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            return List.of();
        }

        @Override
        public void markAsPublished(String eventId) {
        }

        @Override
        public void markAsFailed(
                String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        }

        @Override
        public void reactivate(String eventId) {
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            return List.of();
        }

        @Override
        public int recoverStuckDispatching(Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus status, Instant cutoff, int batchSize) {
            return 0;
        }
    }
}
