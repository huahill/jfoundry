package io.huahill.jfoundry.autoconfigure;

import tools.jackson.databind.ObjectMapper;
import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.messaging.SendResult;
import io.huahill.jfoundry.application.outbox.DefaultOutboxMaintenance;
import io.huahill.jfoundry.application.outbox.OutboxDispatcher;
import io.huahill.jfoundry.application.outbox.JdkOutboxWorker;
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
