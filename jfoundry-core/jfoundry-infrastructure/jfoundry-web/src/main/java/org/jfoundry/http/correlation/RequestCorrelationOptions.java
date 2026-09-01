package org.jfoundry.http.correlation;

import java.util.Objects;
import java.util.function.Predicate;

/// Immutable policy for inbound request correlation.
public record RequestCorrelationOptions(
        String headerName,
        boolean acceptIncoming,
        boolean writeResponse,
        int maximumLength,
        Predicate<String> pathExclusion) {

    /// Creates the default request-correlation policy.
    public static RequestCorrelationOptions defaults() {
        return new RequestCorrelationOptions("X-Request-Id", true, true,
                RequestCorrelationId.DEFAULT_MAXIMUM_LENGTH, path -> false);
    }

    public RequestCorrelationOptions {
        if (headerName == null || headerName.isBlank() || !isHeaderToken(headerName)) {
            throw new IllegalArgumentException("headerName must be a valid HTTP token");
        }
        RequestCorrelationId.validateMaximumLength(maximumLength);
        Objects.requireNonNull(pathExclusion, "pathExclusion must not be null");
    }

    /// Returns whether the application path is excluded from correlation.
    public boolean isPathExcluded(String path) {
        return pathExclusion.test(Objects.requireNonNull(path, "path must not be null"));
    }

    private static boolean isHeaderToken(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character <= 0x20 || character >= 0x7f || "()<>@,;:\\\\[?={} \t".indexOf(character) >= 0) {
                return false;
            }
        }
        return true;
    }
}
