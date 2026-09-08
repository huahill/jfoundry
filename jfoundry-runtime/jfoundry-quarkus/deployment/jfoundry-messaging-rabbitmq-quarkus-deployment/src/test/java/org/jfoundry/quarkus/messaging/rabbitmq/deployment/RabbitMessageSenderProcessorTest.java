package org.jfoundry.quarkus.messaging.rabbitmq.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMessageSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMessageSenderProcessorTest {

    @Test
    void registersTheRabbitMessageSenderAndOptionsProducer() {
        AdditionalBeanBuildItem beans = new RabbitMessageSenderProcessor().registerRabbitMessageSender();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusRabbitMessageSender.class.getName(),
                "org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitOptionsProducer");
        assertThat(beans.isRemovable()).isFalse();
    }
}
