package org.jfoundry.infrastructure.messaging.spring.sender;

import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SpringRabbitMessageSenderTest {

    private final RabbitOperations rabbitOperations = mock(RabbitOperations.class);
    private final SpringRabbitMessageSender sender = new SpringRabbitMessageSender(rabbitOperations);

    @Test
    void returnsOkWhenRabbitSendCompletes() {
        SendResult result = sender.send(OutboundMessage.of("order.exchange", "order.created", "{}"));

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
        verify(rabbitOperations).convertAndSend(org.mockito.ArgumentMatchers.eq("order.exchange"),
                org.mockito.ArgumentMatchers.eq("order.created"), org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));
    }


    @Test
    void copiesPropagationAndPayloadTypeHeaders() {
        sender.send(new OutboundMessage(
                "order.exchange",
                "order.created",
                "{}",
                MessagePropagation.from(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1"));

        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitOperations).convertAndSend(org.mockito.ArgumentMatchers.eq("order.exchange"),
                org.mockito.ArgumentMatchers.eq("order.created"), org.mockito.ArgumentMatchers.eq("{}"),
                processor.capture());

        Message outbound = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
        processor.getValue().postProcessMessage(outbound);
        assertThat(outbound.getMessageProperties().getHeaders())
                .containsEntry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .containsEntry(OutboundMessage.PAYLOAD_TYPE_HEADER, "sales.order-created.v1");
    }

    @Test
    void returnsFailureWhenRabbitSendFails() {
        doThrow(new IllegalStateException("broker down"))
                .when(rabbitOperations)
                .convertAndSend(org.mockito.ArgumentMatchers.eq("order.exchange"),
                        org.mockito.ArgumentMatchers.eq("order.created"), org.mockito.ArgumentMatchers.eq("{}"),
                        org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));

        SendResult result = sender.send(OutboundMessage.of("order.exchange", "order.created", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }
}
