package org.jfoundry.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.infrastructure.outbox.spring.externalization.DefaultDomainEventOutboxRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Business-side custom DomainEventOutboxRecorder replaces the framework default outbox recorder
/// explicitly.
/// <p>
/// TestApp provides an ObjectMapper bean so DomainEventOutboxRecorderAutoConfiguration's
/// payloadSerializer can be registered normally through @ConditionalOnBean(ObjectMapper.class),
/// completing the DomainEventOutboxRecorder dependency chain.
@SpringBootTest(classes = {
        DomainEventOutboxRecorderConditionTest.TestApp.class,
        DomainEventOutboxRecorderConditionTest.WithCustomOutboxRecorder.class
})
class DomainEventOutboxRecorderConditionTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @TestConfiguration
    static class WithCustomOutboxRecorder {
        @Bean
        DomainEventOutboxRecorder customOutboxRecorder() {
            return events -> {
            };
        }

        @Bean
        OutboxMessageStore stubOutboxMessageStore() {
            return new StubOutboxMessageStore();
        }
    }

    /// OutboxMessageStore stub kept to make the default recorder's infrastructure condition true.
    static class StubOutboxMessageStore implements OutboxMessageStore {
        @Override
        public void append(OutboxMessage entry) {
            // no-op
        }

        @Override
        public List<OutboxMessage> findDispatchable(int limit, Instant now) {
            return List.of();
        }

        @Override
        public void markAsPublished(String eventId) {
            // no-op
        }

        @Override
        public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
            // no-op
        }

        @Override
        public void reactivate(String eventId) {
            // no-op
        }

        @Override
        public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
            return List.of();
        }

        @Override
        public int recoverStuckDispatching(java.time.Instant cutoff) {
            return 0;
        }

        @Override
        public int deleteByStatusAndOccurredAtBefore(
                org.jfoundry.application.outbox.OutboxMessageStatus status,
                java.time.Instant cutoff, int batchSize) {
            return 0;
        }
    }

    private DefaultDomainEventOutboxRecorder defaultOutboxRecorder;

    @Autowired
    private DomainEventOutboxRecorder outboxRecorder;

    @Test
    void customOutboxRecorderReplacesDefaultOutboxRecorder() {
        assertThat(outboxRecorder).isNotNull();
        assertThat(defaultOutboxRecorder).isNull();
    }
}
