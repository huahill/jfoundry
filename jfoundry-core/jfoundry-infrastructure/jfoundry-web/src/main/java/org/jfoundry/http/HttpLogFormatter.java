package org.jfoundry.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Renders HTTP diagnostic log events for every runtime adapter.
///
/// Adapters decide *when* to log and *which* details the configured {@link HttpLoggingLevel} allows.
/// This type only shapes those details as either compact `INLINE` records or Feign-style `HUMAN`
/// lines. Each returned string is one log record; adapters must emit them separately.
public final class HttpLogFormatter {

    private static final String EMPTY_BODY = "<empty>";

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
            lines.add("HTTP " + side.label() + " request");
            lines.add(method + " " + uri);
            addHumanHeaders(lines, headers);
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
            var lines = new ArrayList<String>();
            lines.add("HTTP " + side.label() + " request body");
            lines.add(method + " " + uri);
            addBodyLines(lines, body);
            return List.copyOf(lines);
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
            var lines = new ArrayList<String>();
            lines.add("HTTP " + side.label() + " response body");
            lines.add(method + " " + uri + " -> " + status);
            addBodyLines(lines, body);
            return List.copyOf(lines);
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
            lines.add("HTTP " + side.label() + " response");
            var summary = method + " " + uri + " -> " + status + " (" + durationMillis + "ms";
            if (completion != null) {
                summary += ", " + completion;
            }
            lines.add(summary + ")");
            addHumanHeaders(lines, headers);
            if (!isBlankHumanBody(body)) {
                addBodyLines(lines, body);
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
            lines.add("HTTP " + side.label() + " request failed");
            var summary = method + " " + uri + " (" + durationMillis + "ms";
            if (completion != null) {
                summary += ", " + completion;
            }
            lines.add(summary + ")");
            if (exceptionType != null) {
                lines.add(exceptionType);
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
            return List.of("HTTP client response metadata could not be read", method + " " + uri);
        }
        return List.of("HTTP client response metadata could not be read for logging: method=" + method
                + ", uri=" + uri);
    }

    /// Renders a client-side failure to read the response body.
    public static List<String> responseBodyUnavailable(
            HttpLoggingFormat format, String method, String uri, Object status) {
        Objects.requireNonNull(format, "format must not be null");
        if (format == HttpLoggingFormat.HUMAN) {
            return List.of("HTTP client response body could not be read", method + " " + uri + " -> " + status);
        }
        return List.of("HTTP client response body could not be read for logging: method=" + method + ", uri="
                + uri + ", status=" + status);
    }

    static String prettyJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        var first = json.charAt(0);
        if (first != '{' && first != '[') {
            return json;
        }
        var out = new StringBuilder(json.length() + 32);
        var indent = 0;
        var inString = false;
        var escape = false;
        for (var index = 0; index < json.length(); index++) {
            var current = json.charAt(index);
            if (inString) {
                out.append(current);
                if (escape) {
                    escape = false;
                } else if (current == '\\') {
                    escape = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            switch (current) {
                case '"' -> {
                    inString = true;
                    out.append(current);
                }
                case '{', '[' -> {
                    out.append(current);
                    var next = nextNonWhitespace(json, index + 1);
                    if (next >= 0 && ((current == '{' && json.charAt(next) == '}')
                            || (current == '[' && json.charAt(next) == ']'))) {
                        out.append(json.charAt(next));
                        index = next;
                    } else {
                        indent++;
                        newline(out, indent);
                    }
                }
                case '}', ']' -> {
                    indent = Math.max(0, indent - 1);
                    newline(out, indent);
                    out.append(current);
                }
                case ',' -> {
                    out.append(current);
                    newline(out, indent);
                }
                case ':' -> out.append(": ");
                default -> {
                    if (!Character.isWhitespace(current)) {
                        out.append(current);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void addHumanHeaders(List<String> lines, Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    var value = entry.getValue() == null || entry.getValue().isEmpty()
                            ? "" : String.join(", ", entry.getValue());
                    lines.add(entry.getKey() + ": " + value);
                });
    }

    private static void addBodyLines(List<String> lines, String body) {
        for (var line : prettyJson(body).split("\n", -1)) {
            lines.add(line);
        }
    }

    private static boolean isBlankHumanBody(String body) {
        return body == null || body.isEmpty() || EMPTY_BODY.equals(body);
    }

    private static int nextNonWhitespace(String json, int start) {
        for (var index = start; index < json.length(); index++) {
            if (!Character.isWhitespace(json.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void newline(StringBuilder out, int indent) {
        out.append('\n');
        out.append("  ".repeat(indent));
    }
}
