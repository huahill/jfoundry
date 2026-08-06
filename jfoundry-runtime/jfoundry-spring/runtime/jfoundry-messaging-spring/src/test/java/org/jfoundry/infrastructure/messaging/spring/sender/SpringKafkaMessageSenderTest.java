package org.jfoundry.infrastructure.messaging.spring.sender;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
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
    void returnsFailureWhenKafkaSendFails() {
        when(kafkaOperations.send(any(ProducerRecord.class)))
                .thenThrow(new IllegalStateException("broker down"));

        SendResult result = sender.send(OutboundMessage.of("order.created", "order-1", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }
}
