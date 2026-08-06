package org.jfoundry.quarkus.messaging.rabbitmq.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMqMessageSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqMessageSenderProcessorTest {

    @Test
    void registersTheRabbitMqMessageSenderAndOptionsProducer() {
        AdditionalBeanBuildItem beans = new RabbitMqMessageSenderProcessor().registerRabbitMqMessageSender();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusRabbitMqMessageSender.class.getName(),
                "org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMqOptionsProducer");
        assertThat(beans.isRemovable()).isFalse();
    }
}
