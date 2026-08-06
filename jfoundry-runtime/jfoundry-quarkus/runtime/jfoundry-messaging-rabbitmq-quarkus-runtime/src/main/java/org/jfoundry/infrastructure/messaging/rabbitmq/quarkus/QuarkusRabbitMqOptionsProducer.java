package org.jfoundry.infrastructure.messaging.rabbitmq.quarkus;

import io.quarkus.arc.DefaultBean;
import io.smallrye.common.annotation.Identifier;
import io.vertx.rabbitmq.RabbitMQOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/// Provides the default RabbitMQ client options when the application does not configure them.
@ApplicationScoped
public final class QuarkusRabbitMqOptionsProducer {

    @Produces
    @DefaultBean
    @Identifier("jfoundry-rabbitmq")
    RabbitMQOptions rabbitMqOptions() {
        return new RabbitMQOptions();
    }
}
