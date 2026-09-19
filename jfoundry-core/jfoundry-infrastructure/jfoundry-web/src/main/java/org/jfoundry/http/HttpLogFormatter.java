package org.jfoundry.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Renders HTTP diagnostic log events for every runtime adapter.
///
/// Adapters decide *when* to log and *which* details the configured {@link HttpLoggingLevel} allows.
/// This type only shapes those details as either compact `INLINE` records or Feign-style `HUMAN`
/// lines. Each returned string is one log record; adapters must emit them separately. `HUMAN`
/// prefixes request lines with `-->` and response lines with `<--`, and keeps JSON bodies on a
/// single line so large payloads do not explode into many records. Concurrent exchanges can still
/// interleave; the logger's thread or MDC prefix is what groups a request together.
public final class HttpLogFormatter {

    private static final String EMPTY_BODY = "<empty>";

    private static final String REQUEST_MARK = "--> ";

    private static final String RESPONSE_MARK = "<-- ";

    private HttpLogFormatter() {
    }

    /// Renders the request-start records, optionally including headers.
    ///
    /// Pass `headers` only when the logging level includes them; `null` omits header output.
    public static List<String> request(
            HttpLoggingFormat format,
            HttpLoggingSide side,
            String method,
            String uri,
            Map<String, List<String>> headers) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            var lines = new ArrayList<String>();
            lines.add(REQUEST_MARK + method + " " + uri);
            addHumanHeaders(lines, REQUEST_MARK, headers);
            return List.copyOf(lines);
        }
        var messages = new ArrayList<String>(2);
        messages.add("HTTP " + side.label() + " request: method=" + method + ", uri=" + uri);
        if (headers != null) {
            messages.add("HTTP " + side.label() + " request headers: method=" + method + ", uri=" + uri
                    + ", headers=" + headers);
        }
        return List.copyOf(messages);
    }

    /// Renders the captured request-body records.
    ///
    /// `HUMAN` omits empty bodies because they add noise without information. `INLINE` preserves the
    /// historical empty-body line.
    public static List<String> requestBody(
            HttpLoggingFormat format,
            HttpLoggingSide side,
            String method,
            String uri,
            String body) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (format == HttpLoggingFormat.HUMAN && isBlankHumanBody(body)) {
            return List.of();
        }
        if (format == HttpLoggingFormat.HUMAN) {
            return List.of(REQUEST_MARK + method + " " + uri + " [body]", REQUEST_MARK + body);
        }
        return List.of("HTTP " + side.label() + " request body: method=" + method + ", uri=" + uri
                + ", body=" + body);
    }

    /// Renders the captured response-body records.
    ///
    /// `HUMAN` omits empty bodies. `INLINE` preserves the historical empty-body line.
    public static List<String> responseBody(
            HttpLoggingFormat format,
            HttpLoggingSide side,
            String method,
            String uri,
            Object status,
            String body) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (format == HttpLoggingFormat.HUMAN && isBlankHumanBody(body)) {
            return List.of();
        }
        if (format == HttpLoggingFormat.HUMAN) {
            return List.of(RESPONSE_MARK + method + " " + uri + " " + status + " [body]", RESPONSE_MARK + body);
        }
        return List.of("HTTP " + side.label() + " response body: method=" + method + ", uri=" + uri
                + ", status=" + status + ", body=" + body);
    }

    /// Renders the terminal response records, optionally including headers and body.
    ///
    /// `completion` is included when the adapter records a completion reason, such as Spring's
    /// inbound Servlet filter. Pass `headers` or `body` only when the logging level includes them.
    public static List<String> response(
            HttpLoggingFormat format,
            HttpLoggingSide side,
            String method,
            String uri,
            Object status,
            String completion,
            long durationMillis,
            Map<String, List<String>> headers,
            String body) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            var lines = new ArrayList<String>();
            var summary = RESPONSE_MARK + method + " " + uri + " " + status + " (" + durationMillis + "ms";
            if (completion != null) {
                summary += ", " + completion;
            }
            lines.add(summary + ")");
            addHumanHeaders(lines, RESPONSE_MARK, headers);
            if (!isBlankHumanBody(body)) {
                lines.add(RESPONSE_MARK + body);
            }
            return List.copyOf(lines);
        }
        var messages = new ArrayList<String>(3);
        var summary = new StringBuilder();
        summary.append("HTTP ").append(side.label()).append(" response: method=").append(method)
                .append(", uri=").append(uri).append(", status=").append(status);
        if (completion != null) {
            summary.append(", completion=").append(completion);
        }
        summary.append(", duration=").append(durationMillis).append("ms");
        messages.add(summary.toString());
        if (headers != null) {
            messages.add("HTTP " + side.label() + " response headers: method=" + method + ", uri=" + uri
                    + ", status=" + status + ", headers=" + headers);
        }
        if (body != null) {
            messages.add("HTTP " + side.label() + " response body: method=" + method + ", uri=" + uri
                    + ", status=" + status + ", body=" + body);
        }
        return List.copyOf(messages);
    }

    /// Renders a failed exchange as one or more log records.
    public static List<String> failure(
            HttpLoggingFormat format,
            HttpLoggingSide side,
            String method,
            String uri,
            String completion,
            String exceptionType,
            long durationMillis) {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(side, "side must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            var lines = new ArrayList<String>();
            var summary = REQUEST_MARK + method + " " + uri + " failed (" + durationMillis + "ms";
            if (completion != null) {
                summary += ", " + completion;
            }
            lines.add(summary + ")");
            if (exceptionType != null) {
                lines.add(REQUEST_MARK + exceptionType);
            }
            return List.copyOf(lines);
        }
        var text = new StringBuilder();
        text.append("HTTP ").append(side.label()).append(" request failed: method=").append(method)
                .append(", uri=").append(uri);
        if (completion != null) {
            text.append(", completion=").append(completion);
        }
        if (exceptionType != null) {
            text.append(", exception=").append(exceptionType);
        }
        text.append(", duration=").append(durationMillis).append("ms");
        return List.of(text.toString());
    }

    /// Renders a client-side failure to read response metadata.
    public static List<String> responseMetadataUnavailable(HttpLoggingFormat format, String method, String uri) {
        Objects.requireNonNull(format, "format must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            return List.of(RESPONSE_MARK + method + " " + uri + " [metadata unavailable]");
        }
        return List.of("HTTP client response metadata could not be read for logging: method=" + method
                + ", uri=" + uri);
    }

    /// Renders a client-side failure to read the response body.
    public static List<String> responseBodyUnavailable(
            HttpLoggingFormat format, String method, String uri, Object status) {
        Objects.requireNonNull(format, "format must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            return List.of(RESPONSE_MARK + method + " " + uri + " " + status + " [body unavailable]");
        }
        return List.of("HTTP client response body could not be read for logging: method=" + method + ", uri="
                + uri + ", status=" + status);
    }

    private static void addHumanHeaders(List<String> lines, String mark, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    var value = entry.getValue() == null || entry.getValue().isEmpty()
                            ? "" : String.join(", ", entry.getValue());
                    lines.add(mark + entry.getKey() + ": " + value);
                });
    }

    private static boolean isBlankHumanBody(String body) {
        return body == null || body.isEmpty() || EMPTY_BODY.equals(body);
    }

}
