package org.jfoundry.problem;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/// Runtime-neutral representation of an RFC 9457 problem response.
public record ProblemDescriptor(URI type, String title, int status, String detail, Map<String, Object> extensions) {

    private static final java.util.Set<String> RESERVED_MEMBERS =
            java.util.Set.of("type", "title", "status", "detail", "instance");

    public ProblemDescriptor {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(detail, "detail must not be null");
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("status must be an HTTP error status");
        }
        extensions = Map.copyOf(Objects.requireNonNull(extensions, "extensions must not be null"));
        if (extensions.keySet().stream().anyMatch(RESERVED_MEMBERS::contains)) {
            throw new IllegalArgumentException("extensions must not override RFC 9457 members");
        }
    }

}
