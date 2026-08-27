package org.jfoundry.web.spring.client;

/// Classifies safe categories of failures before an HTTP response is available.
public enum HttpRequestFailureKind {
    CONNECTION,
    TIMEOUT,
    TLS,
    RESPONSE_DECODING,
    UNKNOWN
}
