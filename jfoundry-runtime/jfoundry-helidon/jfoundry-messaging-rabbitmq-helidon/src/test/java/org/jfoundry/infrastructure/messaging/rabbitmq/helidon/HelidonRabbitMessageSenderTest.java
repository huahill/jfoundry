package org.jfoundry.infrastructure.messaging.rabbitmq.helidon;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.messaging.OutboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelidonRabbitMessageSenderTest {

    @Test
    void isAReplaceableCdiDefault() {
        assertThat(HelidonRabbitMessageSender.class.isAnnotationPresent(ApplicationScoped.class)).isTrue();
        assertThat(HelidonRabbitMessageSender.class.isAnnotationPresent(Alternative.class)).isTrue();
        assertThat(HelidonRabbitMessageSender.class.getAnnotation(Priority.class).value()).isEqualTo(1);
        assertThat(Modifier.isFinal(HelidonRabbitMessageSender.class.getModifiers())).isFalse();
    }

    @Test
    void publishesThePayloadToTheRequestedExchangeAndRoutingKey() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        var result = sender.send(OutboundMessage.of("orders", "order-42", "{\"id\":42}"));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<byte[]> payload = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq("orders"), eq("order-42"), any(AMQP.BasicProperties.class), payload.capture());
        assertThat(payload.getValue()).isEqualTo("{\"id\":42}".getBytes(StandardCharsets.UTF_8));
        verify(channel).close();
    }

    @Test
    void mapsANullPayloadKeyToAnEmptyRoutingKey() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        var result = sender.send(OutboundMessage.of("orders", null, "{}"));

        assertThat(result.success()).isTrue();
        verify(channel).basicPublish(eq("orders"), eq(""), any(AMQP.BasicProperties.class), any());
    }

    @Test
    void copiesPropagationEntriesAsRabbitHeaders() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        var result = sender.send(new OutboundMessage(
                "orders",
                "order-42",
                "{}",
                MessagePropagation.from(Map.of(
                        "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<AMQP.BasicProperties> properties = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        verify(channel).basicPublish(eq("orders"), eq("order-42"), properties.capture(), any());
        assertThat(properties.getValue().getHeaders())
                .containsEntry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
    }

    @Test
    void connectsLazilyAndCreatesAChannelPerSend() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel firstChannel = mock(Channel.class);
        Channel secondChannel = mock(Channel.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(firstChannel, secondChannel);
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        assertThat(sender.send(OutboundMessage.of("orders", "order-1", "{}")).success()).isTrue();
        assertThat(sender.send(OutboundMessage.of("orders", "order-2", "{}")).success()).isTrue();

        verify(connectionFactory, times(1)).newConnection();
        verify(connection, times(2)).createChannel();
        verify(firstChannel).close();
        verify(secondChannel).close();
    }

    @Test
    void returnsFailureWhenRabbitSendFails() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        when(connectionFactory.newConnection()).thenThrow(new IllegalStateException("broker down"));
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        var result = sender.send(OutboundMessage.of("orders", "order-42", "{}"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("broker down");
    }

    @Test
    void closesTheConnectionOnPreDestroy() throws Exception {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        Connection connection = mock(Connection.class);
        Channel channel = mock(Channel.class);
        when(connectionFactory.newConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(connection.createChannel()).thenReturn(channel);
        HelidonRabbitMessageSender sender = new HelidonRabbitMessageSender(connectionFactory);

        assertThat(sender.send(OutboundMessage.of("orders", "order-42", "{}")).success()).isTrue();
        sender.close();

        verify(connection).close();
    }
}
