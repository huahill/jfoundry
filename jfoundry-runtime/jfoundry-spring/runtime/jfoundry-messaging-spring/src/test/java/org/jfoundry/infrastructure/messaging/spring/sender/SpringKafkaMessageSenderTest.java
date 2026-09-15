package org.jfoundry.infrastructure.messaging.spring.sender;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringKafkaMessageSenderTest {

    @SuppressWarnings("unchecked")
    private final KafkaOperations<String, String> kafkaOperations = mock(KafkaOperations.class);
    private final SpringKafkaMessageSender sender = new SpringKafkaMessageSender(
            kafkaOperations,
            Duration.ofSeconds(1));

    @Test
    void returnsOkWhenKafkaSendCompletes() {
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        SendResult result = sender.send(OutboundMessage.of("order.created", "order-1", "{}"));

        assertThat(result.success()).isTrue();
        assertThat(result.errorMessage()).isNull();
        verify(kafkaOperations).send(any(ProducerRecord.class));
    }


    @Test
    void copiesPropagationAndPayloadTypeHeaders() {
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        sender.send(new OutboundMessage(
                "order.created",
                "order-1",
                "{}",
                MessagePropagation.from(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1"));

        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaOperations).send(record.capture());
        assertThat(record.getValue().headers().lastHeader("traceparent").value())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));
        assertThat(record.getValue().headers().lastHeader(OutboundMessage.PAYLOAD_TYPE_HEADER).value())
                .isEqualTo("sales.order-created.v1".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsFailureWhenKafkaSendFails() {
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenThrow(new IllegalStateException("broker down"));

        SendResult result = sender.send(OutboundMessage.of("order.created", "order-1", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }
}
