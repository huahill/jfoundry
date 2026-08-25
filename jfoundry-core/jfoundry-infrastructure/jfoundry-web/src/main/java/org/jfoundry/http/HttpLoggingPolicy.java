package org.jfoundry.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Defines the runtime-neutral security limits for JFoundry HTTP diagnostic logging.
public final class HttpLoggingPolicy {

    /// Maximum number of body bytes retained for diagnostic logging.
    public static final int MAX_BODY_BYTES = 8 * 1024;

    /// Replacement used for sensitive values.
    public static final String REDACTED = "<redacted>";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy_authorization", "cookie", "set_cookie", "x_api_key", "api_key",
            "x_auth_token", "x_access_token", "x_client_secret", "credential", "credentials");

    private static final Set<String> SENSITIVE_JSON_FIELDS = Set.of(
            "access_token", "api_key", "apikey", "authorization", "client_secret", "cookie", "id_token",
            "password", "refresh_token", "secret", "token", "credential", "credentials");

    private HttpLoggingPolicy() {
    }

    /// Returns the URI without user information, query, or fragment data.
    public static String withoutQuery(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("uri could not be sanitized", exception);
        }
    }

    /// Returns an immutable header description with sensitive values replaced.
    public static Map<String, List<String>> describeHeaders(Map<String, ? extends List<?>> headers) {
        Objects.requireNonNull(headers, "headers must not be null");
        var described = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> described.put(name, isSensitiveHeader(name)
                ? List.of(REDACTED)
                : values.stream().map(String::valueOf).toList()));
        return Map.copyOf(described);
    }

    /// Returns whether a header name is covered by the shared sensitive-value policy.
    public static boolean isSensitiveHeader(String name) {
        var normalized = normalize(Objects.requireNonNull(name, "name must not be null"));
        return SENSITIVE_HEADERS.contains(normalized) || normalized.endsWith("_token")
                || normalized.endsWith("_secret") || normalized.endsWith("_api_key")
                || normalized.endsWith("_credential") || normalized.endsWith("_credentials");
    }

    /// Returns whether a JSON field name is covered by the shared sensitive-value policy.
    public static boolean isSensitiveJsonField(String fieldName) {
        var normalized = normalize(Objects.requireNonNull(fieldName, "fieldName must not be null"));
        return SENSITIVE_JSON_FIELDS.contains(normalized) || normalized.contains("password")
                || normalized.contains("secret") || normalized.contains("token")
                || normalized.endsWith("api_key") || normalized.contains("credential");
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace('-', '_').replace('.', '_');
    }
}
