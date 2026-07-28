package org.jfoundry.autoconfigure.messaging.kafka;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.infrastructure.messaging.spring.sender.SpringKafkaMessageSender;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaOperations;

import java.time.Duration;

@AutoConfiguration
@AutoConfigureAfter(KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaOperations.class)
public class KafkaMessageSenderAutoConfiguration {

    @Bean
    @ConditionalOnBean(KafkaOperations.class)
    @ConditionalOnMissingBean(MessageSender.class)
    public SpringKafkaMessageSender kafkaMessageSender(KafkaOperations<String, String> kafkaOperations) {
        return new SpringKafkaMessageSender(kafkaOperations, Duration.ofSeconds(10));
    }
}
