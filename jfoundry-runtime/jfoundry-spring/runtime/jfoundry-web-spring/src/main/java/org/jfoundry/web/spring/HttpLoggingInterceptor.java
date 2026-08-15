package org.jfoundry.web.spring;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/// Records outbound HTTP client activity at the configured level.
///
/// Logging is inactive unless this type's logger is enabled at `DEBUG`. `BASIC` and `HEADERS` never read,
/// buffer, or wrap request and response bodies. `FULL` redacts JSON fields and captures at most 8 KiB of a
/// body; it can therefore read an unconsumed error response body when that response is closed.
public final class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private static final int MAX_LOGGED_BODY_BYTES = 8 * 1024;

    private static final String REDACTED = "<redacted>";

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "api-key",
            "x-auth-token", "x-access-token", "x-client-secret");

    private static final Set<String> SENSITIVE_JSON_FIELDS = Set.of(
            "access_token", "api_key", "apikey", "authorization", "client_secret", "cookie", "id_token",
            "password", "refresh_token", "secret", "token");

    private final HttpLoggingLevel level;

    private final BooleanSupplier debugEnabled;

    /// Creates an interceptor with the requested logging detail.
    public HttpLoggingInterceptor(HttpLoggingLevel level) {
        this(level, LOG::isDebugEnabled);
    }

    HttpLoggingInterceptor(HttpLoggingLevel level, BooleanSupplier debugEnabled) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.debugEnabled = Objects.requireNonNull(debugEnabled, "debugEnabled must not be null");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (this.level == HttpLoggingLevel.NONE || !this.debugEnabled.getAsBoolean()) {
            return execution.execute(request, body);
        }
        logRequest(request, body);
        try {
            var response = execution.execute(request, body);
            logResponse(response);
            return this.level.includesBodies() ? new LoggingClientHttpResponse(response) : response;
        } catch (IOException | RuntimeException exception) {
            LOG.debug("HTTP request failed: method={}, uri={}", request.getMethod(), withoutQuery(request.getURI()));
            throw exception;
        }
    }

    private void logRequest(HttpRequest request, byte[] body) {
        if (this.level.includesBodies()) {
            LOG.debug("HTTP request: method={}, uri={}, headers={}, body={}", request.getMethod(),
                    withoutQuery(request.getURI()), describeHeaders(request.getHeaders()),
                    describeBody(request.getHeaders(), body, true, false));
        } else if (this.level.includesHeaders()) {
            LOG.debug("HTTP request: method={}, uri={}, headers={}", request.getMethod(), withoutQuery(request.getURI()),
                    describeHeaders(request.getHeaders()));
        } else {
            LOG.debug("HTTP request: method={}, uri={}", request.getMethod(), withoutQuery(request.getURI()));
        }
    }

    private void logResponse(ClientHttpResponse response) throws IOException {
        if (this.level.includesHeaders()) {
            LOG.debug("HTTP response: status={}, headers={}", response.getStatusCode(), describeHeaders(response.getHeaders()));
        } else {
            LOG.debug("HTTP response: status={}", response.getStatusCode());
        }
    }

    private static String withoutQuery(URI uri) {
        return UriComponentsBuilder.fromUri(uri).replaceQuery(null).build(true).toUriString();
    }

    private static Map<String, List<String>> describeHeaders(HttpHeaders headers) {
        var redacted = new LinkedHashMap<String, List<String>>();
        headers.forEach((name, values) -> redacted.put(name,
                isSensitiveHeader(name) ? List.of(REDACTED) : List.copyOf(values)));
        return Map.copyOf(redacted);
    }

    private static boolean isSensitiveHeader(String name) {
        var lowerCase = name.toLowerCase(Locale.ROOT);
        var normalized = lowerCase.replace('-', '_');
        return SENSITIVE_HEADERS.contains(lowerCase) || SENSITIVE_HEADERS.contains(normalized)
                || normalized.contains("token") || normalized.contains("secret") || normalized.endsWith("_api_key");
    }

    private static String describeBody(HttpHeaders headers, byte[] body, boolean complete, boolean truncated) {
        if (!complete) {
            return "<not fully consumed>";
        }
        if (truncated || body.length > MAX_LOGGED_BODY_BYTES) {
            return "<truncated at " + MAX_LOGGED_BODY_BYTES + " bytes>";
        }
        if (body.length == 0) {
            return "<empty>";
        }
        var contentType = headers.getContentType();
        if (!isJson(contentType)) {
            return "<omitted: content-type=" + contentType + ">";
        }
        try {
            var json = ObjectMapperHolder.INSTANCE.readTree(new String(body, StandardCharsets.UTF_8));
            if (!json.isObject() && !json.isArray()) {
                return "<omitted: JSON scalar>";
            }
            redactJson(json);
            return json.toString();
        } catch (RuntimeException exception) {
            return "<omitted: invalid JSON>";
        }
    }

    private static boolean isJson(MediaType contentType) {
        return contentType != null && (MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                || contentType.getSubtype().endsWith("+json"));
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
        var normalized = fieldName.toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_JSON_FIELDS.contains(normalized) || normalized.contains("password")
                || normalized.contains("secret") || normalized.contains("token");
    }

    private static final class ObjectMapperHolder {

        private static final ObjectMapper INSTANCE = new ObjectMapper();

        private ObjectMapperHolder() {
        }
    }

    private static final class LoggingClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;

        private BodyLoggingInputStream body;

        private LoggingClientHttpResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return this.delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return this.delegate.getStatusText();
        }

        @Override
        public InputStream getBody() throws IOException {
            if (this.body == null) {
                this.body = new BodyLoggingInputStream(this.delegate.getBody(), this.delegate.getHeaders());
            }
            return this.body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.delegate.getHeaders();
        }

        @Override
        public void close() {
            logUnconsumedErrorBody();
            if (this.body != null) {
                this.body.logBody();
            }
            this.delegate.close();
        }

        private void logUnconsumedErrorBody() {
            try {
                if (this.body == null && this.delegate.getStatusCode().isError()) {
                    this.body = new BodyLoggingInputStream(this.delegate.getBody(), this.delegate.getHeaders());
                    this.body.readNBytes(MAX_LOGGED_BODY_BYTES + 1);
                }
            } catch (IOException exception) {
                LOG.debug("HTTP response body could not be read for logging");
            }
        }
    }

    private static final class BodyLoggingInputStream extends FilterInputStream {

        private final HttpHeaders headers;

        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private boolean complete;

        private boolean truncated;

        private boolean skipped;

        private boolean logged;

        private int capturedAtMark = -1;

        private BodyLoggingInputStream(InputStream delegate, HttpHeaders headers) {
            super(delegate);
            this.headers = headers;
        }

        @Override
        public int read() throws IOException {
            var value = super.read();
            if (value == -1) {
                this.complete = true;
                logBody();
            } else {
                capture(value);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            var read = super.read(bytes, offset, length);
            if (read == -1) {
                this.complete = true;
                logBody();
            } else if (read > 0) {
                capture(bytes, offset, read);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            var skippedBytes = super.skip(count);
            if (skippedBytes > 0) {
                this.skipped = true;
                this.complete = false;
            }
            return skippedBytes;
        }

        @Override
        public synchronized void mark(int readLimit) {
            super.mark(readLimit);
            this.capturedAtMark = this.captured.size();
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            if (this.capturedAtMark >= 0 && this.captured.size() > this.capturedAtMark) {
                this.skipped = true;
            }
            this.captured.reset();
            this.complete = false;
            this.truncated = false;
            this.logged = false;
            this.capturedAtMark = -1;
        }

        @Override
        public void close() throws IOException {
            logBody();
            super.close();
        }

        private void capture(byte[] bytes, int offset, int length) {
            var remaining = MAX_LOGGED_BODY_BYTES - this.captured.size();
            if (remaining <= 0) {
                this.truncated = true;
                return;
            }
            var capturedLength = Math.min(remaining, length);
            this.captured.write(bytes, offset, capturedLength);
            this.truncated = capturedLength < length;
        }

        private void capture(int value) {
            if (this.captured.size() < MAX_LOGGED_BODY_BYTES) {
                this.captured.write(value);
            } else {
                this.truncated = true;
            }
        }

        private void logBody() {
            if (this.logged) {
                return;
            }
            this.logged = true;
            LOG.debug("HTTP response body: {}", describeBody(this.headers, this.captured.toByteArray(),
                    this.complete && !this.skipped, this.truncated));
        }
    }
}
