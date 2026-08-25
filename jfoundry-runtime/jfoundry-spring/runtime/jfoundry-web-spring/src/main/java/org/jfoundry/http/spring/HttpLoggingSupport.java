package org.jfoundry.http.spring;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
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
    public static final int MAX_BODY_BYTES = 8 * 1024;

    private static final String REDACTED = "<redacted>";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy_authorization", "cookie", "set_cookie", "x_api_key", "api_key",
            "x_auth_token", "x_access_token", "x_client_secret", "credential", "credentials");

    private static final Set<String> SENSITIVE_JSON_FIELDS = Set.of(
            "access_token", "api_key", "apikey", "authorization", "client_secret", "cookie", "id_token",
            "password", "refresh_token", "secret", "token", "credential", "credentials");

    private HttpLoggingSupport() {
    }

    /// Returns the URI without query or fragment data.
    public static String withoutQuery(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        return UriComponentsBuilder.fromUri(uri).userInfo(null).replaceQuery(null).fragment(null)
                .build(true).toUriString();
    }

    /// Returns an immutable header description with sensitive values replaced.
    public static Map<String, List<String>> describeHeaders(
            MultiValueMap<String, String> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        var described = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> described.put(name,
                isSensitiveHeader(name) ? List.of(REDACTED) : List.copyOf(values)));
        return Map.copyOf(described);
    }

    /// Returns an immutable Spring HTTP header description with sensitive values replaced.
    public static Map<String, List<String>> describeHeaders(HttpHeaders headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        var described = new LinkedHashMap<String, List<String>>();
        headers.headerSet().forEach(entry -> described.put(entry.getKey(),
                isSensitiveHeader(entry.getKey()) ? List.of(REDACTED) : List.copyOf(entry.getValue())));
        return Map.copyOf(described);
    }

    /// Returns whether a header name is covered by the shared sensitive-value policy.
    public static boolean isSensitiveHeader(String name) {
        var normalized = normalize(Objects.requireNonNull(name, "name must not be null"));
        return SENSITIVE_HEADERS.contains(normalized) || normalized.endsWith("_token")
                || normalized.endsWith("_secret") || normalized.endsWith("_api_key")
                || normalized.endsWith("_credential") || normalized.endsWith("_credentials");
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
        var normalized = normalize(fieldName);
        return SENSITIVE_JSON_FIELDS.contains(normalized) || normalized.contains("password")
                || normalized.contains("secret") || normalized.contains("token")
                || normalized.endsWith("api_key") || normalized.contains("credential");
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
    }

    private static final class ObjectMapperHolder {

        private static final ObjectMapper INSTANCE = new ObjectMapper();

        private ObjectMapperHolder() {
        }
    }
}
