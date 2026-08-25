package org.jfoundry.http;

/// Selects the detail recorded for HTTP client or server diagnostic logs.
///
/// `BASIC` records request and response metadata without accessing either body. `HEADERS` additionally
/// records redacted headers. `FULL` records redacted and size-limited JSON bodies.
public enum HttpLoggingLevel {

    /// Disables HTTP logging.
    NONE,

    /// Records the HTTP method, query-free URI, response status, duration, and failure metadata.
    BASIC,

    /// Records `BASIC` data and redacted request and response headers.
    HEADERS,

    /// Records `HEADERS` data and redacted, size-limited JSON request and response bodies.
    FULL;

    /// Returns whether headers are included at this level.
    public boolean includesHeaders() {
        return this == HEADERS || this == FULL;
    }

    /// Returns whether bodies are included at this level.
    public boolean includesBodies() {
        return this == FULL;
    }
}
