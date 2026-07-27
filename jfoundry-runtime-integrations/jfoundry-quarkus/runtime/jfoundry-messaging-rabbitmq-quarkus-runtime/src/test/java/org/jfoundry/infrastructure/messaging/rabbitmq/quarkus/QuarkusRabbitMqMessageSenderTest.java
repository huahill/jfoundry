package org.jfoundry.infrastructure.messaging.rabbitmq.quarkus;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;
import org.jfoundry.application.messaging.OutboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuarkusRabbitMqMessageSenderTest {

    @Test
    void publishesThePayloadToTheRequestedExchangeAndRoutingKey() {
        RabbitMQClient client = mock(RabbitMQClient.class);
        when(client.isConnected()).thenReturn(true);
        when(client.basicPublish(org.mockito.ArgumentMatchers.eq("orders"), org.mockito.ArgumentMatchers.eq("order-42"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(Buffer.buffer("{\"id\":42}"))))
                .thenReturn(Future.succeededFuture());
        QuarkusRabbitMqMessageSender sender = new QuarkusRabbitMqMessageSender(client);

        var result = sender.send(OutboundMessage.of("orders", "order-42", "{\"id\":42}"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Buffer> payload = ArgumentCaptor.forClass(Buffer.class);
        verify(client).basicPublish(org.mockito.ArgumentMatchers.eq("orders"),
                org.mockito.ArgumentMatchers.eq("order-42"), org.mockito.ArgumentMatchers.any(), payload.capture());
        assertThat(payload.getValue().toString()).isEqualTo("{\"id\":42}");
    }
}
