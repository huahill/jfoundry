package org.jfoundry.http.spring.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jfoundry.http.spring.HttpLoggingLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth("secret");
            return headers;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }
    };

    private Logger logger;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(HttpLoggingInterceptor.class);
        logger.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void recordsSuccessfulExecutionDurationAtResponseHeaders() throws IOException {
        var response = new TrackingResponse(HttpStatus.OK, "accepted");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.BASIC, () -> true,
                nanos(10_000_000L, 34_999_999L));

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);

        assertThat(actual).isSameAs(response);
        assertThat(messages()).containsExactly(
                "HTTP client request: method=GET, uri=https://downstream.test/orders/42",
                "HTTP client response: status=200 OK, durationMs=24");
        assertThat(response.bodyAccesses()).isZero();
    }

    @Test
    void recordsFailureTypeAndDurationThenRethrowsTheSameException() {
        var failure = new IOException("unavailable");
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.BASIC, () -> true,
                nanos(5_000_000L, 12_000_000L));

        assertThatThrownBy(() -> interceptor.intercept(REQUEST, new byte[0], (request, body) -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(messages()).last().isEqualTo(
                "HTTP client request failed: method=GET, uri=https://downstream.test/orders/42, "
                        + "exception=java.io.IOException, durationMs=7");
    }

    @Test
    void noneAndDisabledDebugDoNotQueryClockOrWrapBodies() throws IOException {
        var clockQueries = new AtomicInteger();
        LongSupplier clock = () -> {
            clockQueries.incrementAndGet();
            return 0;
        };
        var response = new TrackingResponse(HttpStatus.NOT_FOUND, "not found");

        var none = new HttpLoggingInterceptor(HttpLoggingLevel.NONE, () -> true, clock);
        var disabled = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> false, clock);

        assertThat(none.intercept(REQUEST, "secret".getBytes(), (request, body) -> response)).isSameAs(response);
        assertThat(disabled.intercept(REQUEST, "secret".getBytes(), (request, body) -> response)).isSameAs(response);
        assertThat(clockQueries).hasValue(0);
        assertThat(response.bodyAccesses()).isZero();
        assertThat(messages()).isEmpty();
    }

    @Test
    void fullLoggingRedactsBodiesAndReadsAnUnconsumedErrorBodyOnClose() throws IOException {
        var response = new TrackingResponse(HttpStatus.NOT_FOUND, "{\"token\":\"hidden\"}");
        response.headers().setContentType(MediaType.APPLICATION_JSON);
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> true,
                nanos(0, 1_000_000));

        var actual = interceptor.intercept(REQUEST, "{\"password\":\"hidden\"}".getBytes(),
                (request, body) -> response);
        actual.close();

        assertThat(actual).isNotSameAs(response);
        assertThat(response.bodyAccesses()).isEqualTo(1);
        assertThat(messages()).noneMatch(message -> message.contains("hidden"))
                .anyMatch(message -> message.contains("\"password\":\"<redacted>\""))
                .anyMatch(message -> message.contains("\"token\":\"<redacted>\""));
    }

    @Test
    void fullLoggingLeavesAConsumedResponseBodyReadable() throws IOException {
        var response = new TrackingResponse(HttpStatus.OK, "{\"result\":\"accepted\"}");
        response.headers().setContentType(MediaType.APPLICATION_JSON);
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.FULL, () -> true,
                nanos(0, 1_000_000));

        var actual = interceptor.intercept(REQUEST, new byte[0], (request, body) -> response);

        assertThat(new String(actual.getBody().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"result\":\"accepted\"}");
        assertThat(response.bodyAccesses()).isEqualTo(1);
    }

    @Test
    void responseMetadataLoggingFailureDoesNotReplaceTheResponse() throws IOException {
        var response = new TrackingResponse(HttpStatus.OK, "accepted") {
            @Override
            public HttpStatusCode getStatusCode() throws IOException {
                throw new IOException("metadata unavailable");
            }
        };
        var interceptor = new HttpLoggingInterceptor(HttpLoggingLevel.BASIC, () -> true,
                nanos(0, 1_000_000));

        assertThat(interceptor.intercept(REQUEST, new byte[0], (request, body) -> response)).isSameAs(response);
        assertThat(messages()).last().isEqualTo("HTTP client response metadata could not be read for logging");
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static LongSupplier nanos(long... values) {
        var index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }

    private static class TrackingResponse implements ClientHttpResponse {

        private final HttpStatusCode statusCode;

        private final byte[] body;

        private final HttpHeaders headers = new HttpHeaders();

        private int bodyAccesses;

        private TrackingResponse(HttpStatusCode statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
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
            return this.headers;
        }

        @Override
        public void close() {
        }

        private HttpHeaders headers() {
            return this.headers;
        }

        private int bodyAccesses() {
            return this.bodyAccesses;
        }
    }
}
