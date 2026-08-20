package org.jfoundry.web.spring;

import org.jfoundry.application.exception.ExternalAccessException;

import java.util.Objects;

/// Indicates a safe classification of an HTTP request failure.
public final class HttpRequestException extends ExternalAccessException {

    private final HttpRequestFailureKind failureKind;

    /// Creates a classified failure without an underlying transport exception.
    public HttpRequestException(HttpRequestFailureKind failureKind) {
        super("HTTP request failed: " + Objects.requireNonNull(failureKind, "failureKind must not be null"));
        this.failureKind = failureKind;
    }

    /// Creates a classified failure while retaining its cause for server-side diagnostics.
    public HttpRequestException(HttpRequestFailureKind failureKind, Throwable cause) {
        super("HTTP request failed: " + Objects.requireNonNull(failureKind, "failureKind must not be null"),
                Objects.requireNonNull(cause, "cause must not be null"));
        this.failureKind = failureKind;
    }

    public HttpRequestFailureKind failureKind() {
        return failureKind;
    }
}
