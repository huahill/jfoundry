package org.jfoundry.infrastructure.messaging.rabbitmq.quarkus;

import io.quarkus.arc.DefaultBean;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.common.annotation.Identifier;
import com.rabbitmq.client.AMQP.BasicProperties;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;

import java.util.HashMap;

/// Quarkus RabbitMQ-backed {@link MessageSender}.
@ApplicationScoped
@DefaultBean
public class QuarkusRabbitMqMessageSender implements MessageSender {

    private final RabbitMQClient client;
    @Inject
    public QuarkusRabbitMqMessageSender(
            Vertx vertx,
            @Identifier("jfoundry-rabbitmq") RabbitMQOptions options) {
        this(RabbitMQClient.create(vertx, options));
    }

    QuarkusRabbitMqMessageSender(RabbitMQClient client) {
        this.client = client;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        try {
            if (!client.isConnected()) {
                await(client.start());
            }
            BasicProperties properties = new BasicProperties.Builder()
                    .headers(new HashMap<>(message.propagation().entries()))
                    .build();
            await(client.basicPublish(message.topic(), message.payloadKey() == null ? "" : message.payloadKey(),
                    properties, Buffer.buffer(message.payload())));
            return SendResult.ok();
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return SendResult.fail(cause.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        client.stop();
    }

    private void await(Future<Void> future) throws Exception {
        future.toCompletionStage().toCompletableFuture().get();
    }
}
