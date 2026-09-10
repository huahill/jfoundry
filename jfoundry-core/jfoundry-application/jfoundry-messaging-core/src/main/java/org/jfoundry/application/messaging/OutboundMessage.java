package org.jfoundry.application.messaging;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;

/// Immutable outbound transport envelope with routing, payload, and trace propagation metadata.
public record OutboundMessage(String topic, @Nullable String payloadKey, String payload,
                              MessagePropagation propagation, @Nullable String payloadType) {

    /// Envelope header carrying the stored payload contract name.
    public static final String PAYLOAD_TYPE_HEADER = "jfoundry.payload-type";

    public OutboundMessage {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(propagation, "propagation must not be null");
        if (payloadType != null && payloadType.isBlank()) {
            payloadType = null;
        }
    }

    /// Creates an outbound message without upstream trace context or a payload-type contract name.
    public static OutboundMessage of(String topic, @Nullable String payloadKey, String payload) {
        return new OutboundMessage(topic, payloadKey, payload, MessagePropagation.empty(), null);
    }

    /// Copies propagation entries, then the payload-type contract header when present.
    public void applyHeaders(BiConsumer<String, String> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        propagation.entries().forEach(headers);
        if (payloadType != null) {
            headers.accept(PAYLOAD_TYPE_HEADER, payloadType);
        }
    }
}
