package org.jfoundry.infrastructure.messaging.kafka.helidon;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/// Helidon Kafka-backed {@link MessageSender}.
@ApplicationScoped
@Alternative
@Priority(1)
public class HelidonKafkaMessageSender implements MessageSender {

    private final Producer<String, String> producer;
    private final Duration sendTimeout;

    @Inject
    public HelidonKafkaMessageSender(
            @ConfigProperty(name = "jfoundry.messaging.kafka.bootstrap-servers", defaultValue = "localhost:9092")
            String bootstrapServers,
            @ConfigProperty(name = "jfoundry.messaging.kafka.send-timeout", defaultValue = "1s")
            Duration sendTimeout) {
        this(createProducer(bootstrapServers), sendTimeout);
    }

    HelidonKafkaMessageSender(Producer<String, String> producer, Duration sendTimeout) {
        this.producer = producer;
        this.sendTimeout = sendTimeout;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    message.topic(), message.payloadKey(), message.payload());
            message.propagation().entries().forEach((key, value) ->
                    record.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
            producer.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return SendResult.ok();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.fail(exception.getMessage());
        } catch (Exception exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return SendResult.fail(cause.getMessage());
        }
    }

    @PreDestroy
    void close() {
        try {
            producer.close();
        } catch (Exception ignored) {
            // Shutdown must not fail application stop.
        }
    }

    private static Producer<String, String> createProducer(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(properties);
    }
}
