package org.jfoundry.infrastructure.messaging.kafka.quarkus;

import io.quarkus.arc.DefaultBean;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeaders;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.OutboundMessage;
import org.jfoundry.application.messaging.SendResult;

/// Quarkus Kafka-backed {@link MessageSender}.
@ApplicationScoped
@DefaultBean
public class QuarkusKafkaMessageSender implements MessageSender {

    private final MutinyEmitter<String> emitter;
    @Inject
    public QuarkusKafkaMessageSender(@Channel("jfoundry-kafka") MutinyEmitter<String> emitter) {
        this.emitter = emitter;
    }

    @Override
    public SendResult send(OutboundMessage message) {
        try {
            RecordHeaders headers = new RecordHeaders();
            message.propagation().entries().forEach((key, value) ->
                    headers.add(key, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                    .withTopic(message.topic())
                    .withKey(message.payloadKey())
                    .withHeaders(headers)
                    .build();
            emitter.sendMessage(Message.of(message.payload()).addMetadata(metadata)).await().indefinitely();
            return SendResult.ok();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return SendResult.fail(cause.getMessage());
        }
    }
}
