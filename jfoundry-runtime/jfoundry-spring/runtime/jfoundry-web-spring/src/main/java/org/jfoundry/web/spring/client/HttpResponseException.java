package org.jfoundry.web.spring.client;

import org.jfoundry.application.exception.ExternalAccessException;
import org.springframework.http.HttpStatusCode;

import java.util.Objects;

/// Indicates a non-success HTTP response without retaining its headers or body.
public final class HttpResponseException extends ExternalAccessException {

    private final int statusCode;

    public HttpResponseException(HttpStatusCode statusCode) {
        super("HTTP response failed with status " + Objects.requireNonNull(statusCode, "statusCode must not be null").value());
        this.statusCode = statusCode.value();
    }

    public int statusCode() {
        return statusCode;
    }
}
