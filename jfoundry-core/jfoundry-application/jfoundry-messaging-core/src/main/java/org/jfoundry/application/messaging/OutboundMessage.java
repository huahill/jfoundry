package org.jfoundry.application.messaging;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// Immutable outbound transport envelope with routing, payload, and trace propagation metadata.
public record OutboundMessage(String topic, @Nullable String payloadKey, String payload,
                              MessagePropagation propagation) {

    public OutboundMessage {
        Objects.requireNonNull(topic, "topic must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(propagation, "propagation must not be null");
    }

    /// Creates an outbound message without upstream trace context.
    public static OutboundMessage of(String topic, @Nullable String payloadKey, String payload) {
        return new OutboundMessage(topic, payloadKey, payload, MessagePropagation.empty());
    }
}
