package io.huahill.jfoundry.quarkus.messaging.rabbitmq.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.huahill.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitMessageSender;
import io.huahill.jfoundry.infrastructure.messaging.rabbitmq.quarkus.QuarkusRabbitOptionsProducer;

/// Registers the RabbitMQ MessageSender with Quarkus during augmentation.
class RabbitMessageSenderProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerRabbitMessageSender() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusRabbitMessageSender.class)
                .addBeanClass(QuarkusRabbitOptionsProducer.class)
                .setUnremovable()
                .build();
    }
}
