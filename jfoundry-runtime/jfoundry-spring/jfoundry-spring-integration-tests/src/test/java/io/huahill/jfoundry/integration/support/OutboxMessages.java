package io.huahill.jfoundry.integration.support;

import io.huahill.jfoundry.application.outbox.OutboxMessage;

import java.time.Instant;

public final class OutboxMessages {

    private OutboxMessages() {
    }

    public static OutboxMessage pending(String eventId) {
        return pending(eventId, "jfoundry.integration.outbox", null, "{\"event\":\"created\"}");
    }

    public static OutboxMessage pending(String eventId, String topic, String key, String payload) {
        return OutboxMessage.newPending(eventId, topic, key, "io.huahill.jfoundry.integration.TestEvent", payload, Instant.now());
    }

    public static String payloadOfSize(int bytes) {
        if (bytes < 2) {
            throw new IllegalArgumentException("bytes must be at least 2");
        }
        return "{\"data\":\"" + "x".repeat(bytes - 11) + "\"}";
    }
}
