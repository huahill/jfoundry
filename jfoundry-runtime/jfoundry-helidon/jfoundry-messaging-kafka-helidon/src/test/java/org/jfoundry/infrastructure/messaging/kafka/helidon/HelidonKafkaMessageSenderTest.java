package org.jfoundry.infrastructure.messaging.kafka.helidon;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelidonKafkaMessageSenderTest {

    @SuppressWarnings("unchecked")
    private final Producer<String, String> producer = mock(Producer.class);
    private final HelidonKafkaMessageSender sender = new HelidonKafkaMessageSender(
            producer,
            Duration.ofSeconds(1));

    @AfterEach
    void clearInterruptedStatus() {
        Thread.interrupted();
    }

    @Test
    void isAReplaceableCdiDefault() {
        assertThat(HelidonKafkaMessageSender.class.isAnnotationPresent(ApplicationScoped.class)).isTrue();
        assertThat(HelidonKafkaMessageSender.class.isAnnotationPresent(Alternative.class)).isTrue();
        assertThat(HelidonKafkaMessageSender.class.getAnnotation(Priority.class).value()).isEqualTo(1);
        assertThat(Modifier.isFinal(HelidonKafkaMessageSender.class.getModifiers())).isFalse();
    }

    @Test
    void sendsThePayloadWithTheRequestedTopicAndKey() throws Exception {
        @SuppressWarnings("unchecked")
        Future<RecordMetadata> future = mock(Future.class);
        when(producer.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(1_000L, TimeUnit.MILLISECONDS)).thenReturn(null);

        SendResult result = sender.send(OutboundMessage.of("order.created.v1", "order-42", "{\"id\":42}"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(record.capture());
        assertThat(record.getValue().topic()).isEqualTo("order.created.v1");
        assertThat(record.getValue().key()).isEqualTo("order-42");
        assertThat(record.getValue().value()).isEqualTo("{\"id\":42}");
    }

    @Test
    void copiesPropagationEntriesAsKafkaHeaders() throws Exception {
        @SuppressWarnings("unchecked")
        Future<RecordMetadata> future = mock(Future.class);
        when(producer.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(null);

        SendResult result = sender.send(new OutboundMessage(
                "order.created.v1",
                "order-42",
                "{}",
                MessagePropagation.from(Map.of(
                        "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(record.capture());
        assertThat(record.getValue().headers().lastHeader("traceparent").value())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void returnsFailureWhenKafkaSendFails() {
        when(producer.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("broker down"));

        SendResult result = sender.send(OutboundMessage.of("order.created.v1", "order-42", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }

    @Test
    void restoresTheInterruptFlagWhenKafkaSendIsInterrupted() throws Exception {
        @SuppressWarnings("unchecked")
        Future<RecordMetadata> future = mock(Future.class);
        when(producer.send(any(ProducerRecord.class))).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrupted"));

        SendResult result = sender.send(OutboundMessage.of("order.created.v1", "order-42", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void closesTheProducerOnPreDestroy() {
        sender.close();

        verify(producer).close();
    }
}
