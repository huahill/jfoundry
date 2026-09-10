package org.jfoundry.infrastructure.messaging.spring.sender;

import org.apache.rocketmq.client.producer.MQProducer;
import org.apache.rocketmq.common.message.Message;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringRocketMessageSenderTest {

    private final MQProducer producer = mock(MQProducer.class);
    private final SpringRocketMessageSender sender = new SpringRocketMessageSender(producer, Duration.ofSeconds(1));

    @Test
    void sendsTopicKeyAndUtf8Payload() throws Exception {
        SendResult result = sender.send(OutboundMessage.of("order.created", "order-1", "{}"));

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture(), eq(1_000L));
        assertThat(message.getValue().getTopic()).isEqualTo("order.created");
        assertThat(message.getValue().getKeys()).isEqualTo("order-1");
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("{}");
    }


    @Test
    void copiesPropagationAndPayloadTypeUserProperties() throws Exception {
        sender.send(new OutboundMessage(
                "order.created",
                "order-1",
                "{}",
                MessagePropagation.from(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1"));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture(), eq(1_000L));
        assertThat(message.getValue().getUserProperty("traceparent")).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(message.getValue().getUserProperty(OutboundMessage.PAYLOAD_TYPE_HEADER))
                .isEqualTo("sales.order-created.v1");
    }

    @Test
    void omitsKeysWhenPayloadKeyIsNull() throws Exception {
        sender.send(OutboundMessage.of("order.created", null, "{}"));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture(), eq(1_000L));
        assertThat(message.getValue().getKeys()).isNull();
    }

    @Test
    void returnsFailureWhenRocketSendFails() throws Exception {
        when(producer.send(any(Message.class), eq(1_000L)))
                .thenThrow(new IllegalStateException("broker down"));

        SendResult result = sender.send(OutboundMessage.of("order.created", "order-1", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }
}
