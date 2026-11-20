package io.huahill.jfoundry.quarkus.messaging.kafka.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.huahill.jfoundry.infrastructure.messaging.kafka.quarkus.QuarkusKafkaMessageSender;

/// Registers the Kafka MessageSender with Quarkus during augmentation.
class KafkaMessageSenderProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerKafkaMessageSender() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusKafkaMessageSender.class)
                .setUnremovable()
                .build();
    }
}
