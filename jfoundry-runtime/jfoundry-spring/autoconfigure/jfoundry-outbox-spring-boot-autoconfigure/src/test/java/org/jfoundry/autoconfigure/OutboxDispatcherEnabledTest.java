package org.jfoundry.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.DefaultOutboxMaintenance;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.JdkOutboxWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/// {@code jfoundry.outbox.dispatcher.enabled=false} keeps the dispatch service and turns off
/// the process-local JDK worker.
@SpringBootTest(
        classes = OutboxDispatcherEnabledTest.TestApp.class,
        properties = "jfoundry.outbox.dispatcher.enabled=false"
)
class OutboxDispatcherEnabledTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MessageSender messageSender() {
            return outbound -> SendResult.ok();
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void disabledWorkerKeepsDispatcherAndMaintenance() {
        assertThat(context.getBeansOfType(OutboxDispatcher.class)).hasSize(1);
        assertThat(context.getBeansOfType(DefaultOutboxMaintenance.class)).hasSize(1);
        assertThat(context.getBeansOfType(JdkOutboxWorker.class)).isEmpty();
    }
}
