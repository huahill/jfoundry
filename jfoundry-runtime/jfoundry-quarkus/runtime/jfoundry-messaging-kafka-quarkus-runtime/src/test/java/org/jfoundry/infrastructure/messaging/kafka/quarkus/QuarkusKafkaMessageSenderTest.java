package org.jfoundry.infrastructure.messaging.kafka.quarkus;

import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarkusKafkaMessageSenderTest {

    @Test
    void sendsThePayloadWithTheRequestedTopicAndKey() {
        MutinyEmitter<String> emitter = mock(MutinyEmitter.class);
        when(emitter.sendMessage(any(Message.class))).thenReturn(io.smallrye.mutiny.Uni.createFrom().voidItem());
        QuarkusKafkaMessageSender sender = new QuarkusKafkaMessageSender(emitter);

        SendResult result = sender.send(new OutboundMessage(
                "order.created.v1",
                "order-42",
                "{\"id\":42}",
                MessagePropagation.from(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Message<String>> message = ArgumentCaptor.forClass(Message.class);
        verify(emitter).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).isEqualTo("{\"id\":42}");
        OutgoingKafkaRecordMetadata<?> metadata = message.getValue()
                .getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow();
        assertThat(metadata.getTopic()).isEqualTo("order.created.v1");
        assertThat(metadata.getKey()).isEqualTo("order-42");
        assertThat(metadata.getHeaders().lastHeader("traceparent").value())
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));
        assertThat(metadata.getHeaders().lastHeader(OutboundMessage.PAYLOAD_TYPE_HEADER).value())
                .isEqualTo("sales.order-created.v1".getBytes(StandardCharsets.UTF_8));
    }
}
