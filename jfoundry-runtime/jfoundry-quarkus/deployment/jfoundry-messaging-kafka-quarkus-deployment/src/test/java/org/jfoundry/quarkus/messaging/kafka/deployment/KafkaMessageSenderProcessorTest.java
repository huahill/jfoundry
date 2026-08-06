package org.jfoundry.quarkus.messaging.kafka.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.messaging.kafka.quarkus.QuarkusKafkaMessageSender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMessageSenderProcessorTest {

    @Test
    void registersTheKafkaMessageSender() {
        AdditionalBeanBuildItem beans = new KafkaMessageSenderProcessor().registerKafkaMessageSender();

        assertThat(beans.getBeanClasses()).contains(QuarkusKafkaMessageSender.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
