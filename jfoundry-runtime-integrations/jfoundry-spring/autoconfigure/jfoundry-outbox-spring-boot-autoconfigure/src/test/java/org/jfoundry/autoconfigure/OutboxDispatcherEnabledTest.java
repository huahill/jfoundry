package org.jfoundry.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/// Regression test: {@code jfoundry.outbox.dispatcher.enabled} is not a public
/// switch anymore. Dispatcher selection is controlled by
/// {@code jfoundry.outbox.dispatcher.mode}.
/// <p>
/// TestApp provides an ObjectMapper bean so DomainEventOutboxRecorderAutoConfiguration's
/// payloadSerializer can be registered normally through @ConditionalOnBean(ObjectMapper.class),
/// completing the DomainEventOutboxRecorder dependency chain. It also provides a MessageSender so
/// this test does not depend on any broker-specific auto-configuration.
@SpringBootTest(
        classes = {OutboxDispatcherEnabledTest.TestApp.class, OutboxDispatcherEnabledTest.DisabledConfig.class},
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

    @TestConfiguration
    static class DisabledConfig {
        // no bean overrides; property alone disables the autoconfig
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void legacyEnabledPropertyDoesNotDisableDispatcher() {
        assertThat(context.getBeansOfType(OutboxDispatcher.class)).hasSize(1);
    }

    @Test
    void legacyEnabledPropertyDoesNotDisableScheduledDispatcher() {
        assertThat(context.containsBeanDefinition("scheduledOutboxDispatcher"))
                .as("scheduledOutboxDispatcher is controlled by mode, not enabled")
                .isTrue();
    }
}
