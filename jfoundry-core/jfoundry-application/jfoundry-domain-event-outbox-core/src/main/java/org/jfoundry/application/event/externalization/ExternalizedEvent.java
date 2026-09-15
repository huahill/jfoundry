package org.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// Transport-neutral description of a versioned integration message.
///
/// @param topic destination topic
/// @param payloadType stable integration-contract name
/// @param payload integration-contract payload
/// @param payloadKey optional routing key
/// @param aggregateType optional aggregate type
/// @param aggregateId optional aggregate identifier
/// @param aggregateVersion optional aggregate version
public record ExternalizedEvent(
        String topic,
        String payloadType,
        Object payload,
        @Nullable String payloadKey,
        @Nullable String aggregateType,
        @Nullable String aggregateId,
        @Nullable Long aggregateVersion) {

    public ExternalizedEvent {
        requireNonBlank(topic, "topic");
        requireNonBlank(payloadType, "payloadType");
        Objects.requireNonNull(payload, "payload must not be null");
        requireOptionalNonBlank(payloadKey, "payloadKey");
        requireOptionalNonBlank(aggregateType, "aggregateType");
        requireOptionalNonBlank(aggregateId, "aggregateId");
        if ((aggregateType == null) != (aggregateId == null)) {
            throw new IllegalArgumentException("aggregateType and aggregateId must either both be set or both be null");
        }
        if (aggregateVersion != null && aggregateType == null) {
            throw new IllegalArgumentException("aggregateVersion requires aggregate metadata");
        }
        if (aggregateVersion != null && aggregateVersion < 0) {
            throw new IllegalArgumentException("aggregateVersion must not be negative");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireOptionalNonBlank(@Nullable String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank when set");
        }
    }
}
