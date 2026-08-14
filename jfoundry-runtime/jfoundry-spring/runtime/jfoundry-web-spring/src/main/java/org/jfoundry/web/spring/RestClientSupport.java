package org.jfoundry.web.spring;

import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.function.Supplier;

/// Installs safe response handling and translates RestClient transport failures.
public final class RestClientSupport {

    private RestClientSupport() {
    }

    public static RestClient.Builder configure(RestClient.Builder builder) {
        return Objects.requireNonNull(builder, "builder must not be null")
                .defaultStatusHandler(new HttpResponseErrorHandler());
    }

    public static <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        try {
            return operation.get();
        } catch (HttpResponseException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new HttpRequestException(classify(exception));
        }
    }

    private static HttpRequestFailureKind classify(RestClientException exception) {
        if (hasCause(exception, SSLException.class)) {
            return HttpRequestFailureKind.TLS;
        }
        if (hasCause(exception, SocketTimeoutException.class) || hasCause(exception, HttpTimeoutException.class)) {
            return HttpRequestFailureKind.TIMEOUT;
        }
        if (exception instanceof ResourceAccessException && (hasCause(exception, ConnectException.class)
                || hasCause(exception, UnknownHostException.class))) {
            return HttpRequestFailureKind.CONNECTION;
        }
        if (hasCause(exception, HttpMessageConversionException.class)) {
            return HttpRequestFailureKind.RESPONSE_DECODING;
        }
        return HttpRequestFailureKind.UNKNOWN;
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable candidate = exception; candidate != null; candidate = candidate.getCause()) {
            if (type.isInstance(candidate)) {
                return true;
            }
        }
        return false;
    }
}
