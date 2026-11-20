package io.huahill.jfoundry.autoconfigure.messaging.rabbitmq;

import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.messaging.SendResult;
import io.huahill.jfoundry.infrastructure.messaging.spring.sender.SpringRabbitMessageSender;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMessageSenderAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RabbitMessageSenderAutoConfiguration.class))
            .withBean(RabbitOperations.class, () -> mock(RabbitOperations.class));

    @Test
    void createsSpringRabbitMessageSenderWhenRabbitOperationsExists() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MessageSender.class);
            assertThat(context.getBean(MessageSender.class)).isInstanceOf(SpringRabbitMessageSender.class);
        });
    }

    @Test
    void backsOffWhenUserProvidesMessageSender() {
        runner.withBean(MessageSender.class, () -> outbound -> SendResult.ok())
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageSender.class);
                    assertThat(context).doesNotHaveBean(SpringRabbitMessageSender.class);
                });
    }

}
