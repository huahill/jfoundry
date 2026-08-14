package org.jfoundry.web.spring;

import org.jfoundry.application.exception.ExternalAccessException;

import java.util.Objects;

/// Indicates a safe classification of an HTTP request failure without retaining the original exception.
public final class HttpRequestException extends ExternalAccessException {

    private final HttpRequestFailureKind failureKind;

    public HttpRequestException(HttpRequestFailureKind failureKind) {
        super("HTTP request failed: " + Objects.requireNonNull(failureKind, "failureKind must not be null"));
        this.failureKind = failureKind;
    }

    public HttpRequestFailureKind failureKind() {
        return failureKind;
    }
}
