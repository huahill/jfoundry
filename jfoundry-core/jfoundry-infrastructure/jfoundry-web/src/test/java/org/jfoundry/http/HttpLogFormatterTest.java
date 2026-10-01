package org.jfoundry.http;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLogFormatterTest {

    private static final Map<String, List<String>> HEADERS;

    static {
        var headers = new LinkedHashMap<String, List<String>>();
        headers.put("Authorization", List.of("<redacted>"));
        headers.put("Accept", List.of("application/json", "text/plain"));
        HEADERS = Map.copyOf(headers);
    }

    @Test
    void inlineRequestKeepsHistoricalOneLineEvents() {
        assertThat(HttpLogFormatter.request(HttpLoggingFormat.INLINE, HttpLoggingSide.SERVER, "GET",
                "https://service.test/orders", HEADERS)).containsExactly(
                "HTTP server request: method=GET, uri=https://service.test/orders",
                "HTTP server request headers: method=GET, uri=https://service.test/orders, headers=" + HEADERS);
    }

    @Test
    void humanRequestPutsEachHeaderOnItsOwnLine() {
        assertThat(HttpLogFormatter.request(HttpLoggingFormat.HUMAN, HttpLoggingSide.SERVER, "GET",
                "https://service.test/orders", HEADERS)).containsExactly(
                "--> GET https://service.test/orders",
                "--> Accept: application/json, text/plain",
                "--> Authorization: <redacted>");
    }

    @Test
    void humanRequestOmitsHeaderSectionWhenHeadersAreAbsent() {
        assertThat(HttpLogFormatter.request(HttpLoggingFormat.HUMAN, HttpLoggingSide.CLIENT, "POST",
                "https://downstream.test/login", null)).containsExactly(
                "--> POST https://downstream.test/login");
    }

    @Test
    void humanOmitsEmptyRequestBodiesWhileInlineKeepsThem() {
        assertThat(HttpLogFormatter.requestBody(HttpLoggingFormat.HUMAN, HttpLoggingSide.SERVER, "GET",
                "https://service.test/orders", "<empty>")).isEmpty();
        assertThat(HttpLogFormatter.requestBody(HttpLoggingFormat.INLINE, HttpLoggingSide.SERVER, "GET",
                "https://service.test/orders", "<empty>")).containsExactly(
                "HTTP server request body: method=GET, uri=https://service.test/orders, body=<empty>");
    }

    @Test
    void humanKeepsJsonRequestBodiesOnOneLine() {
        assertThat(HttpLogFormatter.requestBody(HttpLoggingFormat.HUMAN, HttpLoggingSide.CLIENT, "POST",
                "https://downstream.test/login",
                "{\"account\":\"rdcopen\",\"password\":\"<redacted>\"}")).containsExactly(
                "--> {\"account\":\"rdcopen\",\"password\":\"<redacted>\"}");
    }

    @Test
    void inlineResponsePreservesOptionalCompletionAndSeparateEvents() {
        assertThat(HttpLogFormatter.response(HttpLoggingFormat.INLINE, HttpLoggingSide.SERVER, "POST",
                "https://service.test/orders", 201, "complete", 24, HEADERS, "{\"ok\":true}"))
                .containsExactly(
                        "HTTP server response: method=POST, uri=https://service.test/orders, status=201, "
                                + "completion=complete, duration=24ms",
                        "HTTP server response headers: method=POST, uri=https://service.test/orders, status=201, "
                                + "headers=" + HEADERS,
                        "HTTP server response body: method=POST, uri=https://service.test/orders, status=201, "
                                + "body={\"ok\":true}");
        assertThat(HttpLogFormatter.response(HttpLoggingFormat.INLINE, HttpLoggingSide.CLIENT, "GET",
                "https://downstream.test/orders/42", 200, null, 30, null, null)).containsExactly(
                "HTTP client response: method=GET, uri=https://downstream.test/orders/42, status=200, duration=30ms");
    }

    @Test
    void humanResponseEmitsOneLogRecordPerLine() {
        assertThat(HttpLogFormatter.response(HttpLoggingFormat.HUMAN, HttpLoggingSide.SERVER, "GET",
                "https://service.test/orders", 200, "complete", 64, HEADERS,
                "{\"page\":1,\"items\":[{\"name\":\"Ada\"}]}")).containsExactly(
                "<-- 200 (64ms, complete)",
                "<-- Accept: application/json, text/plain",
                "<-- Authorization: <redacted>",
                "<-- {\"page\":1,\"items\":[{\"name\":\"Ada\"}]}");
    }

    @Test
    void humanEndClosesRequestAndResponseBlocks() {
        assertThat(HttpLogFormatter.end(HttpLoggingFormat.HUMAN, true)).containsExactly("--> END HTTP");
        assertThat(HttpLogFormatter.end(HttpLoggingFormat.HUMAN, false)).containsExactly("<-- END HTTP");
        assertThat(HttpLogFormatter.end(HttpLoggingFormat.INLINE, true)).isEmpty();
        assertThat(HttpLogFormatter.end(HttpLoggingFormat.INLINE, false)).isEmpty();
    }

    @Test
    void humanFailureKeepsExceptionAndDurationReadable() {
        assertThat(HttpLogFormatter.failure(HttpLoggingFormat.HUMAN, HttpLoggingSide.SERVER, "POST",
                "https://service.test/orders", "failed", "java.io.IOException", 12)).containsExactly(
                "--> POST https://service.test/orders failed (12ms, failed)",
                "--> java.io.IOException");
        assertThat(HttpLogFormatter.failure(HttpLoggingFormat.INLINE, HttpLoggingSide.CLIENT, "GET",
                "https://downstream.test/orders/42", null, "java.io.IOException", 7))
                .containsExactly("HTTP client request failed: method=GET, uri=https://downstream.test/orders/42, "
                        + "exception=java.io.IOException, duration=7ms");
    }
}
