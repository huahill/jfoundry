package org.jfoundry.infrastructure.messaging.rabbitmq.quarkus;

import com.rabbitmq.client.AMQP.BasicProperties;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarkusRabbitMessageSenderTest {

    @Test
    void publishesThePayloadToTheRequestedExchangeAndRoutingKey() {
        RabbitMQClient client = mock(RabbitMQClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.basicPublish(org.mockito.ArgumentMatchers.eq("orders"), org.mockito.ArgumentMatchers.eq("order-42"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Buffer.buffer("{\"id\":42}"))))
                .thenReturn(Future.succeededFuture());
        QuarkusRabbitMessageSender sender = new QuarkusRabbitMessageSender(client);

        var result = sender.send(new OutboundMessage(
                "orders",
                "order-42",
                "{\"id\":42}",
                MessagePropagation.from(Map.of("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        ArgumentCaptor<BasicProperties> properties = ArgumentCaptor.forClass(BasicProperties.class);
        verify(client).basicPublish(org.mockito.ArgumentMatchers.eq("orders"),
                org.mockito.ArgumentMatchers.eq("order-42"), properties.capture(), payload.capture());
        assertThat(payload.getValue().toString()).isEqualTo("{\"id\":42}");
        assertThat(properties.getValue().getHeaders())
                .containsEntry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                .containsEntry(OutboundMessage.PAYLOAD_TYPE_HEADER, "sales.order-created.v1");
    }
}
