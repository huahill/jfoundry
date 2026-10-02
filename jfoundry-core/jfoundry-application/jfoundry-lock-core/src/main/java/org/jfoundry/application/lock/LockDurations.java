package org.jfoundry.application.lock;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/// Parses the duration attributes shared by {@link DistributedLock}.
public final class LockDurations {

    private LockDurations() {
    }

    /// Parses a required duration. Blank text is rejected.
    public static Duration required(String value, String attributeName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(attributeName + " must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("p")) {
            return Duration.parse(value.trim());
        }
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.ofMillis(Long.parseLong(normalized));
    }

    /// Parses an optional duration. Blank text selects the backend default.
    public static Optional<Duration> optional(String value, String attributeName) {
        if (value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(required(value, attributeName));
    }
}
