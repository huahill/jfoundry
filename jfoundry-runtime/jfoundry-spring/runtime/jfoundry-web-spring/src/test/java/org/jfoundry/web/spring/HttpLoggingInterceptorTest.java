package org.jfoundry.web.spring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLoggingInterceptorTest {

    private static final HttpRequest REQUEST = new HttpRequest() {
        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create("https://downstream.test/orders/42?access_token=secret");
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    };

    @Test
    void basicLoggingDoesNotWrapOrAccessTheResponseBody() throws IOException {
        var response = new TrackingResponse(HttpStatus.NOT_FOUND, "not found");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.BASIC, () -> true);

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);
        actual.close();

        assertThat(actual).isSameAs(response);
        assertThat(response.bodyAccesses()).isZero();
    }

    @Test
    void fullLoggingDoesNotWrapOrAccessTheResponseBodyWhenDebugIsDisabled() throws IOException {
        var response = new TrackingResponse(HttpStatus.NOT_FOUND, "not found");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> false);

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);
        actual.close();

        assertThat(actual).isSameAs(response);
        assertThat(response.bodyAccesses()).isZero();
    }

    @Test
    void fullLoggingReadsAnUnconsumedErrorResponseBodyWhenClosed() throws IOException {
        var response = new TrackingResponse(HttpStatus.NOT_FOUND, "not found");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> true);

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);
        actual.close();

        assertThat(actual).isNotSameAs(response);
        assertThat(response.bodyAccesses()).isEqualTo(1);
    }

    @Test
    void fullLoggingLeavesAConsumedResponseBodyReadable() throws IOException {
        var response = new TrackingResponse(HttpStatus.OK, "accepted");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> true);

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);

        assertThat(new String(actual.getBody().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("accepted");
        assertThat(response.bodyAccesses()).isEqualTo(1);
    }

    private static final class TrackingResponse implements ClientHttpResponse {

        private final HttpStatusCode statusCode;

        private final byte[] body;

        private int bodyAccesses;

        private TrackingResponse(HttpStatusCode statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return this.statusCode;
        }

        @Override
        public String getStatusText() {
            return this.statusCode.toString();
        }

        @Override
        public InputStream getBody() {
            this.bodyAccesses++;
            return new ByteArrayInputStream(this.body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public void close() {
        }

        private int bodyAccesses() {
            return this.bodyAccesses;
        }
    }
}
