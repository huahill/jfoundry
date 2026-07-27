package org.jfoundry.infrastructure.messaging.spring.sender;

import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/// Spring-managed RocketMQ-backed {@link MessageSender}.
public class SpringRocketMqMessageSender implements MessageSender {

    private final MQProducer producer;
    private final Duration sendTimeout;

    public SpringRocketMqMessageSender(MQProducer producer, Duration sendTimeout) {
        this.producer = producer;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public SendResult send(OutboundMessage outboundMessage) {
        try {
            Message message = new Message(outboundMessage.topic(), outboundMessage.payload().getBytes(StandardCharsets.UTF_8));
            if (outboundMessage.payloadKey() != null) {
                message.setKeys(outboundMessage.payloadKey());
            }
            outboundMessage.propagation().entries().forEach(message::putUserProperty);
            producer.send(message, sendTimeout.toMillis());
            return SendResult.ok();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return SendResult.fail(cause.getMessage());
        }
    }
}
