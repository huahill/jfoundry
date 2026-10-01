package org.jfoundry.http;

/// Distinguishes inbound server logs from outbound client logs.
public enum HttpLoggingSide {

    /// Inbound HTTP processing.
    SERVER("server"),

    /// Outbound HTTP execution.
    CLIENT("client");

    private final String label;

    HttpLoggingSide(String label) {
        this.label = label;
    }

    /// Returns the stable log-label fragment, such as `server` or `client`.
    public String label() {
        return this.label;
    }
}
