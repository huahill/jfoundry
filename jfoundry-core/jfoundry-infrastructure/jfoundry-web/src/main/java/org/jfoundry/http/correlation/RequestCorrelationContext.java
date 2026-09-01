package org.jfoundry.http.correlation;

import java.util.Optional;

/// Request-scoped access to the final correlation identifier.
public interface RequestCorrelationContext {

    String ATTRIBUTE_NAME = "org.jfoundry.http.correlation.id";

    /// Returns the identifier associated with this request.
    RequestCorrelationId id();

    /// Returns the context installed on the current thread, if any.
    static Optional<RequestCorrelationContext> current() {
        return RequestCorrelationContextHolder.current();
    }

    /// Installs a context for the current request thread.
    static void install(RequestCorrelationContext context) {
        RequestCorrelationContextHolder.install(context);
    }

    /// Clears the current request thread's context.
    static void clear() {
        RequestCorrelationContextHolder.clear();
    }

    /// Creates a context for an identifier.
    static RequestCorrelationContext of(RequestCorrelationId id) {
        return new SimpleRequestCorrelationContext(id);
    }

    /// Default immutable context implementation used by runtime adapters.
    record SimpleRequestCorrelationContext(RequestCorrelationId id) implements RequestCorrelationContext {
        public SimpleRequestCorrelationContext {
            java.util.Objects.requireNonNull(id, "id must not be null");
        }
    }
}
