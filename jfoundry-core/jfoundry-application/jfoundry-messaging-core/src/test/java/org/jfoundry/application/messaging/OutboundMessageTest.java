package org.jfoundry.application.messaging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundMessageTest {

    @Test
    void keepsRoutingPayloadAndPropagationAsSeparateMessageParts() {
        MessagePropagation propagation = MessagePropagation.from(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));

        OutboundMessage message = new OutboundMessage("orders", "order-42", "{}", propagation);

        assertThat(message.topic()).isEqualTo("orders");
        assertThat(message.payloadKey()).isEqualTo("order-42");
        assertThat(message.payload()).isEqualTo("{}");
        assertThat(message.propagation()).isSameAs(propagation);
    }

    @Test
    void usesEmptyPropagationWhenNoUpstreamTraceExists() {
        OutboundMessage message = OutboundMessage.of("orders", null, "{}");

        assertThat(message.propagation()).isSameAs(MessagePropagation.empty());
    }
}
