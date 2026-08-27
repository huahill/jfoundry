package org.jfoundry.application.messaging;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Bounded W3C Trace Context metadata carried with an outbound message.
public final class MessagePropagation {

    private static final List<String> ALLOWED_KEYS = List.of("traceparent", "tracestate");
    private static final int MAX_ENTRIES = 2;
    private static final int MAX_VALUE_LENGTH = 512;
    private static final MessagePropagation EMPTY = new MessagePropagation(Map.of());

    private final Map<String, String> entries;

    private MessagePropagation(Map<String, String> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /// Returns propagation with no upstream trace context.
    public static MessagePropagation empty() {
        return EMPTY;
    }

    /// Creates validated, immutable propagation metadata from W3C Trace Context entries.
    public static MessagePropagation from(Map<String, String> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("message propagation supports at most " + MAX_ENTRIES + " entries");
        }

        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = Objects.requireNonNull(entry.getKey(), "propagation key must not be null");
            String value = Objects.requireNonNull(entry.getValue(), "propagation value must not be null");
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("unsupported message propagation key: " + key);
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException("message propagation value for " + key + " must not be blank");
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("message propagation value for " + key + " exceeds "
                        + MAX_VALUE_LENGTH + " characters");
            }
        }

        Map<String, String> validated = new LinkedHashMap<>();
        for (String key : ALLOWED_KEYS) {
            @Nullable String value = entries.get(key);
            if (value != null) {
                validated.put(key, value);
            }
        }
        return validated.isEmpty() ? empty() : new MessagePropagation(validated);
    }

    /// Returns the validated transport entries in deterministic insertion order.
    public Map<String, String> entries() {
        return entries;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof MessagePropagation other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }
}
