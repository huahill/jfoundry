package org.jfoundry.http.correlation;

import java.util.Optional;
import java.util.UUID;

/// Validated identifier used to correlate one inbound HTTP request.
public record RequestCorrelationId(String value) {

    public static final int DEFAULT_MAXIMUM_LENGTH = 64;
    public static final int MINIMUM_GENERATED_LENGTH = 36;

    /// Creates and validates a server-generated UUID identifier.
    public static RequestCorrelationId generate() {
        return new RequestCorrelationId(UUID.randomUUID().toString());
    }

    /// Parses an identifier using the default maximum length.
    public static Optional<RequestCorrelationId> parse(String value) {
        return parse(value, DEFAULT_MAXIMUM_LENGTH);
    }

    /// Parses an identifier using the caller's bounded maximum length.
    public static Optional<RequestCorrelationId> parse(String value, int maximumLength) {
        if (maximumLength < 1 || maximumLength > DEFAULT_MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("maximumLength must be between 1 and 64");
        }
        if (value == null || value.isEmpty() || value.length() > maximumLength || !isValidToken(value)) {
            return Optional.empty();
        }
        return Optional.of(new RequestCorrelationId(value));
    }

    public RequestCorrelationId {
        if (value == null || value.isEmpty() || value.length() > DEFAULT_MAXIMUM_LENGTH || !isValidToken(value)) {
            throw new IllegalArgumentException("value must be a printable correlation token of at most 64 characters");
        }
    }

    static void validateMaximumLength(int maximumLength) {
        if (maximumLength < MINIMUM_GENERATED_LENGTH || maximumLength > DEFAULT_MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("maximumLength must be between 36 and 64");
        }
    }

    private static boolean isValidToken(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && !(character >= '0' && character <= '9')
                    && character != '.' && character != '_' && character != '-' && character != '~') {
                return false;
            }
        }
        return true;
    }
}
