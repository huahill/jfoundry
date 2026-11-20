package io.huahill.jfoundry.infrastructure.messaging.spring.sender;

import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import io.huahill.jfoundry.application.messaging.MessageSender;
import io.huahill.jfoundry.application.messaging.OutboundMessage;
import io.huahill.jfoundry.application.messaging.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/// Spring-managed RocketMQ-backed {@link MessageSender}.
public class SpringRocketMessageSender implements MessageSender {

    private final MQProducer producer;
    private final Duration sendTimeout;

    public SpringRocketMessageSender(MQProducer producer, Duration sendTimeout) {
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
            outboundMessage.applyHeaders(message::putUserProperty);
            producer.send(message, sendTimeout.toMillis());
            return SendResult.ok();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return SendResult.fail(cause.getMessage());
        }
    }
}
