package org.jfoundry.quarkus.messaging.rabbitmq.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMqMessageSender;
import org.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMqOptionsProducer;

/// Registers the RabbitMQ MessageSender with Quarkus during augmentation.
class RabbitMqMessageSenderProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerRabbitMqMessageSender() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusRabbitMqMessageSender.class)
                .addBeanClass(QuarkusRabbitMqOptionsProducer.class)
                .setUnremovable()
                .build();
    }
}
