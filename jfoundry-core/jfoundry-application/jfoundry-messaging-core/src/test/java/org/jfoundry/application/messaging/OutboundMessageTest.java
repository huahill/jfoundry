package org.jfoundry.application.messaging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class OutboundMessageTest {

    @Test
    void keepsRoutingPayloadAndPropagationAsSeparateMessageParts() {
        MessagePropagation propagation = MessagePropagation.from(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"));

        OutboundMessage message = new OutboundMessage("orders", "order-42", "{}", propagation, null);

        assertThat(message.topic()).isEqualTo("orders");
        assertThat(message.payloadKey()).isEqualTo("order-42");
        assertThat(message.payload()).isEqualTo("{}");
        assertThat(message.propagation()).isSameAs(propagation);
        assertThat(message.payloadType()).isNull();
    }

    @Test
    void usesEmptyPropagationWhenNoUpstreamTraceExists() {
        OutboundMessage message = OutboundMessage.of("orders", null, "{}");

        assertThat(message.propagation()).isSameAs(MessagePropagation.empty());
        assertThat(message.payloadType()).isNull();
    }

    @Test
    void keepsPayloadTypeAsAFifthEnvelopeComponent() {
        OutboundMessage message = new OutboundMessage(
                "orders", "order-42", "{}", MessagePropagation.empty(), "sales.order-created.v1");

        assertThat(message.payloadType()).isEqualTo("sales.order-created.v1");
    }

    @Test
    void treatsBlankPayloadTypeAsAbsent() {
        OutboundMessage message = new OutboundMessage(
                "orders", "order-42", "{}", MessagePropagation.empty(), "  ");

        assertThat(message.payloadType()).isNull();
    }

    @Test
    void applyHeadersCopiesPropagationThenPayloadType() {
        Map<String, String> headers = new LinkedHashMap<>();
        OutboundMessage message = new OutboundMessage(
                "orders",
                "order-42",
                "{}",
                MessagePropagation.from(Map.of(
                        "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")),
                "sales.order-created.v1");

        message.applyHeaders(headers::put);

        assertThat(headers).containsExactly(
                entry("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"),
                entry(OutboundMessage.PAYLOAD_TYPE_HEADER, "sales.order-created.v1"));
    }

    @Test
    void applyHeadersOmitsAbsentPayloadType() {
        Map<String, String> headers = new LinkedHashMap<>();

        new OutboundMessage("orders", "order-42", "{}", MessagePropagation.empty(), null)
                .applyHeaders(headers::put);

        assertThat(headers).isEmpty();
    }
}
