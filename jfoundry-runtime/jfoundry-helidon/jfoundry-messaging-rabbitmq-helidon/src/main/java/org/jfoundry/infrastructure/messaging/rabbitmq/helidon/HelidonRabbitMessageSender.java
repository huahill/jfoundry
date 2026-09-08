package org.jfoundry.infrastructure.messaging.rabbitmq.helidon;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/// Helidon RabbitMQ-backed {@link MessageSender}.
@ApplicationScoped
@Alternative
@Priority(1)
public class HelidonRabbitMessageSender implements MessageSender {

    private final ConnectionFactory connectionFactory;
    private volatile @Nullable Connection connection;

    @Inject
    public HelidonRabbitMessageSender(
            @ConfigProperty(name = "jfoundry.messaging.rabbitmq.host", defaultValue = "localhost")
            String host,
            @ConfigProperty(name = "jfoundry.messaging.rabbitmq.port", defaultValue = "5672")
            int port,
            @ConfigProperty(name = "jfoundry.messaging.rabbitmq.username", defaultValue = "guest")
            String username,
            @ConfigProperty(name = "jfoundry.messaging.rabbitmq.password", defaultValue = "guest")
            String password) {
        this(createConnectionFactory(host, port, username, password));
    }

    HelidonRabbitMessageSender(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        try {
            try (Channel channel = connection().createChannel()) {
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .headers(new HashMap<>(message.propagation().entries()))
                        .build();
                channel.basicPublish(
                        message.topic(),
                        message.payloadKey() == null ? "" : message.payloadKey(),
                        properties,
                        message.payload().getBytes(StandardCharsets.UTF_8));
            }
            return SendResult.ok();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return SendResult.fail(cause.getMessage());
        }
    }

    @PreDestroy
    void close() {
        Connection existing = connection;
        connection = null;
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception ignored) {
                // Shutdown must not mask an earlier send failure or fail application stop.
            }
        }
    }

    private Connection connection() throws Exception {
        Connection existing = connection;
        if (existing != null && existing.isOpen()) {
            return existing;
        }
        synchronized (this) {
            existing = connection;
            if (existing == null || !existing.isOpen()) {
                existing = connectionFactory.newConnection();
                connection = existing;
            }
            return existing;
        }
    }

    private static ConnectionFactory createConnectionFactory(String host, int port, String username, String password) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        return connectionFactory;
    }
}
