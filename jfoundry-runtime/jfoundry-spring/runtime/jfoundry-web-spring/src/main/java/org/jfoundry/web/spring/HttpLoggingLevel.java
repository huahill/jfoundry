package org.jfoundry.web.spring;

/// Selects the detail recorded for outbound HTTP client logs.
///
/// `BASIC` records request and response metadata without accessing either body. `HEADERS` additionally
/// records redacted headers. `FULL` records redacted and size-limited JSON bodies, which may read an error
/// response body when the response is closed.
public enum HttpLoggingLevel {

    /// Disables HTTP client logging.
    NONE,

    /// Records the HTTP method, query-free URI, response status, and transport failure metadata.
    BASIC,

    /// Records `BASIC` data and redacted request and response headers.
    HEADERS,

    /// Records `HEADERS` data and redacted, size-limited JSON request and response bodies.
    FULL;

    boolean includesHeaders() {
        return this == HEADERS || this == FULL;
    }

    boolean includesBodies() {
        return this == FULL;
    }
}
