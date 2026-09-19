package org.jfoundry.http.spring;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jfoundry.http.HttpLoggingPolicy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/// Applies the security policy shared by Spring HTTP client and server logging adapters.
///
/// This type removes URI queries, redacts sensitive headers and JSON fields, accepts JSON media types only,
/// and describes body completeness without retaining more than 8 KiB. It does not execute protocol operations
/// or emit logs.
public final class HttpLoggingSupport {

    /// Maximum number of body bytes retained for diagnostic logging.
    public static final int MAX_BODY_BYTES = HttpLoggingPolicy.MAX_BODY_BYTES;

    private static final String REDACTED = "<redacted>";

    private HttpLoggingSupport() {
    }

    /// Returns the URI without query or fragment data.
    public static String withoutQuery(URI uri) {
        return HttpLoggingPolicy.withoutQuery(uri);
    }

    /// Returns an immutable header description limited to the default diagnostic names.
    public static Map<String, List<String>> describeHeaders(
            MultiValueMap<String, String> headers) {
        return HttpLoggingPolicy.describeHeaders(headers);
    }

    /// Returns an immutable header description limited to `includedHeaders`.
    public static Map<String, List<String>> describeHeaders(
            MultiValueMap<String, String> headers, Collection<String> includedHeaders) {
        return HttpLoggingPolicy.describeHeaders(headers, includedHeaders);
    }

    /// Returns an immutable Spring HTTP header description limited to the default diagnostic names.
    public static Map<String, List<String>> describeHeaders(HttpHeaders headers) {
        return HttpLoggingPolicy.describeHeaders(asMap(headers));
    }

    /// Returns an immutable Spring HTTP header description limited to `includedHeaders`.
    public static Map<String, List<String>> describeHeaders(HttpHeaders headers,
            Collection<String> includedHeaders) {
        return HttpLoggingPolicy.describeHeaders(asMap(headers), includedHeaders);
    }

    private static Map<String, List<String>> asMap(HttpHeaders headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        var copy = new LinkedHashMap<String, List<String>>();
        headers.headerSet().forEach(entry -> copy.put(entry.getKey(), entry.getValue()));
        return copy;
    }

    /// Returns whether a header name is covered by the shared sensitive-value policy.
    public static boolean isSensitiveHeader(String name) {
        return HttpLoggingPolicy.isSensitiveHeader(name);
    }

    /// Describes a captured body without exposing non-JSON, malformed, incomplete, or oversized content.
    public static String describeBody(String contentType, byte[] body, boolean complete, boolean truncated) {
        MediaType mediaType = null;
        if (contentType != null) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (RuntimeException exception) {
                return "<omitted: invalid content-type>";
            }
        }
        return describeBody(mediaType, body, complete, truncated);
    }

    /// Describes a captured body without exposing non-JSON, malformed, incomplete, or oversized content.
    public static String describeBody(MediaType contentType, byte[] body, boolean complete, boolean truncated) {
        Objects.requireNonNull(body, "body must not be null");
        if (!complete) {
            return "<not fully consumed>";
        }
        if (truncated || body.length > MAX_BODY_BYTES) {
            return "<truncated at " + MAX_BODY_BYTES + " bytes>";
        }
        if (body.length == 0) {
            return "<empty>";
        }
        if (!isJson(contentType)) {
            return "<omitted: content-type=" + contentType + ">";
        }
        try {
            var json = ObjectMapperHolder.INSTANCE.readTree(new String(body, StandardCharsets.UTF_8));
            if (json == null || (!json.isObject() && !json.isArray())) {
                return "<omitted: JSON scalar>";
            }
            redactJson(json);
            return json.toString();
        } catch (RuntimeException exception) {
            return "<omitted: invalid JSON>";
        }
    }

    /// Returns whether the media type represents JSON or a structured JSON suffix.
    public static boolean isJson(MediaType contentType) {
        return contentType != null && (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json"));
    }

    private static void redactJson(JsonNode node) {
        if (node.isObject()) {
            var object = (ObjectNode) node;
            for (var property : object.properties()) {
                if (isSensitiveJsonField(property.getKey())) {
                    object.put(property.getKey(), REDACTED);
                } else {
                    redactJson(property.getValue());
                }
            }
        } else if (node.isArray()) {
            for (var element : node) {
                redactJson(element);
            }
        }
    }

    private static boolean isSensitiveJsonField(String fieldName) {
        return HttpLoggingPolicy.isSensitiveJsonField(fieldName);
    }

    private static final class ObjectMapperHolder {

        private static final ObjectMapper INSTANCE = new ObjectMapper();

        private ObjectMapperHolder() {
        }
    }
}
