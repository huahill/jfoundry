package org.jfoundry.autoconfigure.messaging.rabbitmq;

import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.infrastructure.messaging.spring.sender.SpringRabbitMessageSender;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMessageSenderAutoConfiguration {

    @Bean
    @ConditionalOnBean(RabbitOperations.class)
    @ConditionalOnMissingBean(MessageSender.class)
    public SpringRabbitMessageSender rabbitMessageSender(RabbitOperations rabbitOperations) {
        return new SpringRabbitMessageSender(rabbitOperations);
    }
}
