package org.jfoundry.application.messaging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MessagePropagationTest {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void retainsOnlyApprovedTraceContextEntries() {
        MessagePropagation propagation = MessagePropagation.from(Map.of(
                "traceparent", TRACEPARENT,
                "tracestate", "vendor=value"));

        assertThat(propagation.entries()).containsExactly(
                Map.entry("traceparent", TRACEPARENT),
                Map.entry("tracestate", "vendor=value"));
    }

    @Test
    void rejectsNonTraceContextEntries() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessagePropagation.from(Map.of("baggage", "userId=42")))
                .withMessageContaining("baggage");
    }

    @Test
    void snapshotsInputAndDoesNotExposeMutableEntries() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("traceparent", TRACEPARENT);

        MessagePropagation propagation = MessagePropagation.from(input);
        input.put("tracestate", "vendor=value");

        assertThat(propagation.entries()).containsExactly(Map.entry("traceparent", TRACEPARENT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessagePropagation.from(Map.of("traceparent", " ")))
                .withMessageContaining("traceparent");
    }

    @Test
    void rejectsMoreEntriesThanTheW3cTraceContextAllows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MessagePropagation.from(Map.of(
                        "traceparent", TRACEPARENT,
                        "tracestate", "vendor=value",
                        "baggage", "userId=42")))
                .withMessageContaining("at most 2 entries");
    }
}
