package org.jfoundry.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jfoundry.domain.entity.agg.BaseAggregateRoot;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.awaitility.Awaitility;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.event.annotation.Externalized;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// End-to-end integration test:
/// @ApplicationService → DomainEventDispatcher → DomainEventOutboxRecorder → Outbox table →
/// ScheduledOutboxDispatcher → CollectingMessageSender.
/// <p>
/// @EnableAutoConfiguration lets Spring Boot load the auto-configuration chain inside
/// jfoundry-spring-boot-autoconfigure according to @AutoConfigureAfter ordering. The test
/// application class is responsible for @MapperScan so mapper beans are registered during
/// ConfigurationClassParser processing.
@SpringBootTest(
        classes = DomainEventExternalizationIntegrationTest.TestApp.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:jfoundry-domain-event-externalization;DB_CLOSE_DELAY=-1",
                "jfoundry.domain.event.dispatch.outbox.enabled=true"
        }
)
class DomainEventExternalizationIntegrationTest {

    @Autowired
    private TestUseCase useCase;

    @Autowired
    private CollectingMessageSender collectingSender;

    @Externalized("env.created")
    static class EnvCreatedEvent extends BaseDomainEvent {
    }

    @Test
    void applicationServiceEventEndsUpAtMessageSender() {
        useCase.create();

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(collectingSender.receivedPayloads).hasSize(1));
        assertThat(collectingSender.receivedPayloads.get(0))
                .contains("\"eventId\":", "\"occurredAt\":")
                .doesNotContain("@class", "EnvCreatedEvent");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        @Primary
        CollectingMessageSender collectingMessageSender() {
            return new CollectingMessageSender();
        }

        @Bean
        TestUseCase testUseCase(DomainEventContext domainEventContext) {
            return new TestUseCase(domainEventContext);
        }
    }

    @ApplicationService
    static class TestUseCase {

        private final DomainEventContext domainEventContext;

        TestUseCase(DomainEventContext domainEventContext) {
            this.domainEventContext = domainEventContext;
        }

        void create() {
            domainEventContext.register(TestAggregate.create());
        }
    }

    static final class TestAggregate extends BaseAggregateRoot<TestAggregate, TestAggregateId> {

        private TestAggregate(TestAggregateId id) {
            super(id);
        }

        static TestAggregate create() {
            TestAggregate aggregate = new TestAggregate(new TestAggregateId("env-1"));
            aggregate.recordEvent(new EnvCreatedEvent());
            return aggregate;
        }
    }

    record TestAggregateId(String value) implements Identifier {
    }

    static class CollectingMessageSender implements MessageSender {
        final List<String> receivedPayloads = new ArrayList<>();

        @Override
        public SendResult send(org.jfoundry.application.messaging.OutboundMessage message) {
            receivedPayloads.add(message.payload());
            return SendResult.ok();
        }
    }
}
