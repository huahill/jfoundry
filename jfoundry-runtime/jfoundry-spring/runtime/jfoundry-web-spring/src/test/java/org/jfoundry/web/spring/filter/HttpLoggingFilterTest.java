package org.jfoundry.web.spring.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletException;
import org.jfoundry.http.spring.HttpLoggingLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpLoggingFilterTest {

    private Logger logger;

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
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
    void noneAndDisabledDebugDelegateWithOriginalObjectsWithoutQueryingClock() throws Exception {
        var clockQueries = new AtomicInteger();
        LongSupplier clock = () -> {
            clockQueries.incrementAndGet();
            return 0;
        };
        var request = request("secret".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        var seenRequest = new AtomicReference<>();
        var seenResponse = new AtomicReference<>();

        new HttpLoggingFilter(HttpLoggingLevel.NONE, () -> true, clock).doFilter(request, response,
                (actualRequest, actualResponse) -> {
                    seenRequest.set(actualRequest);
                    seenResponse.set(actualResponse);
                });

        assertThat(seenRequest).hasValue(request);
        assertThat(seenResponse).hasValue(response);
        assertThat(clockQueries).hasValue(0);
        assertThat(messages()).isEmpty();

        new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> false, clock).doFilter(request, response,
                (actualRequest, actualResponse) -> {
                    assertThat(actualRequest).isSameAs(request);
                    assertThat(actualResponse).isSameAs(response);
                });
        assertThat(clockQueries).hasValue(0);
    }

    @Test
    void basicRecordsQueryFreeLifecycleStatusAndDeterministicDuration() throws Exception {
        var request = request(new byte[0]);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.BASIC, () -> true,
                nanos(10_000_000, 35_999_999));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(actualRequest).isSameAs(request);
            assertThat(actualResponse).isSameAs(response);
            ((MockHttpServletResponse) actualResponse).setStatus(201);
        });

        assertThat(messages()).containsExactly(
                "HTTP server request: method=POST, uri=https://service.test/orders",
                "HTTP server response: method=POST, uri=https://service.test/orders, status=201, "
                        + "completion=complete, durationMs=25");
        assertThat(messages()).noneMatch(message -> message.contains("access_token"));
    }

    @Test
    void headersAreRedactedCaseInsensitivelyInBothDirections() throws Exception {
        var request = request(new byte[0]);
        request.addHeader("AUTHORIZATION", "Bearer request-secret");
        request.addHeader("X-Service-Token", "request-token");
        request.addHeader("Cookie", "session=request-cookie");
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.HEADERS, () -> true, nanos(0, 1_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            var httpResponse = (jakarta.servlet.http.HttpServletResponse) actualResponse;
            httpResponse.setHeader("Set-Cookie", "session=response-cookie");
            httpResponse.setHeader("Vendor-Api-Key", "response-key");
        });

        assertThat(messages()).noneMatch(message -> message.contains("request-secret")
                        || message.contains("request-token") || message.contains("request-cookie")
                        || message.contains("response-cookie") || message.contains("response-key"))
                .anyMatch(message -> message.contains("AUTHORIZATION=[<redacted>]"))
                .anyMatch(message -> message.contains("Set-Cookie=[<redacted>]"))
                .anyMatch(message -> message.contains("Vendor-Api-Key=[<redacted>]"));
    }

    @Test
    void fullLoggingPreservesJsonBytesAndRedactsNestedFields() throws Exception {
        var requestBytes = "{\"用户\":{\"password\":\"秘密\",\"name\":\"小明\"}}"
                .getBytes(StandardCharsets.UTF_8);
        var responseBytes = "{\"items\":[{\"access_token\":\"隐藏\",\"result\":\"成功\"}]}"
                .getBytes(StandardCharsets.UTF_8);
        var request = request(requestBytes);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> true, nanos(0, 2_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(actualRequest.getInputStream().readAllBytes()).containsExactly(requestBytes);
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getOutputStream().write(responseBytes);
            actualResponse.flushBuffer();
        });

        assertThat(response.getContentAsByteArray()).containsExactly(responseBytes);
        assertThat(messages()).noneMatch(message -> message.contains("秘密") || message.contains("隐藏"))
                .anyMatch(message -> message.contains("\"password\":\"<redacted>\""))
                .anyMatch(message -> message.contains("\"access_token\":\"<redacted>\""))
                .anyMatch(message -> message.contains("小明") && message.contains("成功"));
    }

    @Test
    void fullLoggingSupportsWriterFlushAndCloseWithoutDuplicatingOutput() throws Exception {
        var request = request(new byte[0]);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        var filter = new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> true, nanos(0, 1_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            var writer = actualResponse.getWriter();
            writer.write("{\"message\":\"你好\"}");
            writer.flush();
            writer.close();
        });

        assertThat(response.getContentAsString()).isEqualTo("{\"message\":\"你好\"}");
        assertThat(messages()).anyMatch(message -> message.contains("\"message\":\"你好\""));
    }

    @Test
    void fullLoggingCapturesReaderInputAndDiscardsResetResponseBytes() throws Exception {
        var requestBytes = "{\"name\":\"Ada\"}".getBytes(StandardCharsets.ISO_8859_1);
        var request = request(requestBytes);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> true, nanos(0, 1_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(actualRequest.getReader().readLine()).isEqualTo("{\"name\":\"Ada\"}");
            assertThat(actualRequest.getReader().read()).isEqualTo(-1);
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getOutputStream().write("{\"token\":\"discarded-secret\"}"
                    .getBytes(StandardCharsets.UTF_8));
            actualResponse.resetBuffer();
            actualResponse.getOutputStream().write("{\"result\":\"kept\"}".getBytes(StandardCharsets.UTF_8));
        });

        assertThat(response.getContentAsString()).isEqualTo("{\"result\":\"kept\"}");
        assertThat(messages()).noneMatch(message -> message.contains("discarded-secret"))
                .anyMatch(message -> message.contains("requestBody={\"name\":\"Ada\"}"))
                .anyMatch(message -> message.contains("responseBody={\"result\":\"kept\"}"));
    }

    @Test
    void fullLoggingOmitsUnsafeIncompleteInvalidAndOversizedBodies() throws Exception {
        var largeBody = ("{\"value\":\"" + "x".repeat(9_000) + "\"}").getBytes(StandardCharsets.UTF_8);
        var request = request("{\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8));
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> true, nanos(0, 1_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            assertThat(actualRequest.getInputStream().read()).isEqualTo('{');
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getOutputStream().write(largeBody);
        });

        assertThat(response.getContentAsByteArray()).containsExactly(largeBody);
        assertThat(messages()).anyMatch(message -> message.contains("requestBody=<not fully consumed>"))
                .anyMatch(message -> message.contains("responseBody=<truncated at 8192 bytes>"))
                .noneMatch(message -> message.contains("secret"));

        logs.list.clear();
        var invalid = request(new byte[0]);
        var invalidResponse = new MockHttpServletResponse();
        filter = new HttpLoggingFilter(HttpLoggingLevel.FULL, () -> true, nanos(0, 1_000_000));
        filter.doFilter(invalid, invalidResponse, (actualRequest, actualResponse) -> {
            actualResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            actualResponse.getOutputStream().write("{".getBytes(StandardCharsets.UTF_8));
        });
        assertThat(messages()).anyMatch(message -> message.contains("responseBody=<omitted: invalid JSON>"));
    }

    @Test
    void escapingExceptionIsLoggedOnceAndRethrownUnchanged() {
        var request = request(new byte[0]);
        var response = new MockHttpServletResponse();
        var failure = new ServletException("failed");
        var filter = new HttpLoggingFilter(HttpLoggingLevel.BASIC, () -> true, nanos(0, 4_000_000));

        assertThatThrownBy(() -> filter.doFilter(request, response, (actualRequest, actualResponse) -> {
            throw failure;
        })).isSameAs(failure);

        assertThat(messages()).containsExactly(
                "HTTP server request: method=POST, uri=https://service.test/orders",
                "HTTP server request failed: method=POST, uri=https://service.test/orders, completion=failed, "
                        + "exception=jakarta.servlet.ServletException, durationMs=4");
    }

    @Test
    void asyncCompletionUsesInitialStartAndLogsOnlyAtTerminalCompletion() throws Exception {
        var request = request(new byte[0]);
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.BASIC, () -> true, nanos(10_000_000, 42_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> actualRequest.startAsync());

        assertThat(messages()).containsExactly("HTTP server request: method=POST, uri=https://service.test/orders");
        ((MockAsyncContext) request.getAsyncContext()).complete();
        assertThat(messages()).containsExactly(
                "HTTP server request: method=POST, uri=https://service.test/orders",
                "HTTP server response: method=POST, uri=https://service.test/orders, status=200, "
                        + "completion=async-complete, durationMs=32");
    }

    @Test
    void asyncErrorAndTimeoutAreDistinctAndTerminalOnlyOnce() throws Exception {
        var request = request(new byte[0]);
        request.setAsyncSupported(true);
        var response = new MockHttpServletResponse();
        var filter = new HttpLoggingFilter(HttpLoggingLevel.BASIC, () -> true, nanos(0, 3_000_000));

        filter.doFilter(request, response, (actualRequest, actualResponse) -> actualRequest.startAsync());
        var async = (MockAsyncContext) request.getAsyncContext();
        var event = new AsyncEvent(async, new IllegalStateException("async failed"));
        for (AsyncListener listener : async.getListeners()) {
            listener.onError(event);
            listener.onTimeout(new AsyncEvent(async));
            listener.onComplete(new AsyncEvent(async));
        }

        assertThat(messages()).hasSize(2);
        assertThat(messages().getLast()).contains("completion=async-error")
                .contains("exception=java.lang.IllegalStateException")
                .contains("durationMs=3");
    }

    private List<String> messages() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static MockHttpServletRequest request(byte[] content) {
        var request = new MockHttpServletRequest("POST", "/orders");
        request.setScheme("https");
        request.setServerName("service.test");
        request.setServerPort(443);
        request.setQueryString("access_token=secret");
        request.setContent(content);
        return request;
    }

    private static LongSupplier nanos(long... values) {
        var index = new AtomicInteger();
        return () -> values[index.getAndIncrement()];
    }
}
