package io.huahill.jfoundry.autoconfigure.messaging.kafka;

import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.messaging.SendResult;
import io.huahill.jfoundry.infrastructure.messaging.spring.sender.SpringKafkaMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaMessageSenderAutoConfigurationTest {

    @SuppressWarnings("unchecked")
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaMessageSenderAutoConfiguration.class))
            .withBean(KafkaOperations.class, () -> mock(KafkaOperations.class));

    @Test
    void createsSpringKafkaMessageSenderWhenKafkaOperationsExists() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MessageSender.class);
            assertThat(context.getBean(MessageSender.class)).isInstanceOf(SpringKafkaMessageSender.class);
        });
    }

    @Test
    void backsOffWhenUserProvidesMessageSender() {
        runner.withBean(MessageSender.class, () -> outbound -> SendResult.ok())
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageSender.class);
                    assertThat(context).doesNotHaveBean(SpringKafkaMessageSender.class);
                });
    }

    @Test
    void createsKafkaSenderAfterBootCreatesKafkaOperations() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        KafkaAutoConfiguration.class,
                        KafkaMessageSenderAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaOperations.class);
                    assertThat(context).hasSingleBean(MessageSender.class);
                    assertThat(context.getBean(MessageSender.class))
                            .isInstanceOf(SpringKafkaMessageSender.class);
                });
    }

}
