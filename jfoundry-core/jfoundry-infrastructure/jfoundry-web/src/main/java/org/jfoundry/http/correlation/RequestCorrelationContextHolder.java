package org.jfoundry.http.correlation;

import java.util.Optional;

/// Thread-owned holder for the active request context; adapters must clear it at request completion.
public final class RequestCorrelationContextHolder {

    private static final ThreadLocal<RequestCorrelationContext> CURRENT = new ThreadLocal<>();

    private RequestCorrelationContextHolder() {
    }

    static Optional<RequestCorrelationContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void install(RequestCorrelationContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    static void clear() {
        CURRENT.remove();
    }
}
