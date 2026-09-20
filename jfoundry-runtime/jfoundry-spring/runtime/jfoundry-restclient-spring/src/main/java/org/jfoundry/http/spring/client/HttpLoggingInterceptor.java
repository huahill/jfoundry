package org.jfoundry.http.spring.client;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.jfoundry.http.HttpLogFormatter;
import org.jfoundry.http.HttpLoggingFormat;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;
import org.jfoundry.http.HttpLoggingSide;
import org.jfoundry.http.spring.HttpLoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/// Records outbound Spring HTTP execution-chain activity at the configured level.
///
/// Logging is inactive unless this type's logger is enabled at `INFO`. `BASIC` and `HEADERS` never access or
/// wrap bodies. `FULL` captures size-limited JSON bodies and may read an unconsumed error response on close.
/// The `duration` field ends when response headers are available and excludes response-body consumption and decoding.
public final class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private final HttpLoggingLevel level;

    private final HttpLoggingFormat format;

    private final List<String> includedHeaders;

    private final BooleanSupplier infoEnabled;

    private final LongSupplier nanoTime;

    /// Creates an interceptor with the requested logging detail and human-readable layout.
    public HttpLoggingInterceptor(HttpLoggingLevel level) {
        this(level, HttpLoggingFormat.HUMAN, HttpLoggingPolicy.DEFAULT_INCLUDED_HEADERS, LOG::isInfoEnabled,
                System::nanoTime);
    }

    /// Creates an interceptor with the requested logging detail and layout.
    public HttpLoggingInterceptor(HttpLoggingLevel level, HttpLoggingFormat format) {
        this(level, format, HttpLoggingPolicy.DEFAULT_INCLUDED_HEADERS, LOG::isInfoEnabled, System::nanoTime);
    }

    /// Creates an interceptor with the requested logging detail, layout, and included headers.
    public HttpLoggingInterceptor(HttpLoggingLevel level, HttpLoggingFormat format,
            Collection<String> includedHeaders) {
        this(level, format, includedHeaders, LOG::isInfoEnabled, System::nanoTime);
    }

    HttpLoggingInterceptor(HttpLoggingLevel level, BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        this(level, HttpLoggingFormat.INLINE, HttpLoggingPolicy.DEFAULT_INCLUDED_HEADERS, infoEnabled, nanoTime);
    }

    HttpLoggingInterceptor(HttpLoggingLevel level, HttpLoggingFormat format, BooleanSupplier infoEnabled,
            LongSupplier nanoTime) {
        this(level, format, HttpLoggingPolicy.DEFAULT_INCLUDED_HEADERS, infoEnabled, nanoTime);
    }

    HttpLoggingInterceptor(HttpLoggingLevel level, HttpLoggingFormat format, Collection<String> includedHeaders,
            BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.includedHeaders = List.copyOf(Objects.requireNonNull(includedHeaders, "includedHeaders must not be null"));
        this.infoEnabled = Objects.requireNonNull(infoEnabled, "infoEnabled must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (this.level == HttpLoggingLevel.NONE || !this.infoEnabled.getAsBoolean()) {
            return execution.execute(request, body);
        }
        var method = requestMethod(request);
        var uri = requestUri(request);
        logRequest(request, body, method, uri);
        var startedAt = this.nanoTime.getAsLong();
        try {
            var response = execution.execute(request, body);
            var status = logResponseSafely(response, method, uri, elapsedMillis(startedAt));
            return this.level.includesBodies()
                    ? new LoggingClientHttpResponse(this.format, response, method, uri, status) : response;
        } catch (IOException | RuntimeException exception) {
            logAll(HttpLogFormatter.failure(this.format, HttpLoggingSide.CLIENT, method, uri, null,
                    exception.getClass().getName(), elapsedMillis(startedAt)));
            throw exception;
        }
    }

    private void logRequest(HttpRequest request, byte[] body, String method, String uri) {
        logAll(HttpLogFormatter.request(this.format, HttpLoggingSide.CLIENT, method, uri,
                this.level.includesHeaders() ? HttpLoggingSupport.describeHeaders(request.getHeaders(), this.includedHeaders) : null));
        if (this.level.includesBodies()) {
            logAll(HttpLogFormatter.requestBody(this.format, HttpLoggingSide.CLIENT, method, uri,
                    HttpLoggingSupport.describeBody(request.getHeaders().getContentType(), body, true, false)));
        }
    }

    private static String requestMethod(HttpRequest request) {
        try {
            return request.getMethod().name();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }

    private static String requestUri(HttpRequest request) {
        try {
            return HttpLoggingSupport.withoutQuery(request.getURI()).toString();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }

    private Integer logResponseSafely(ClientHttpResponse response, String method, String uri, long durationMillis) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException | RuntimeException exception) {
            logAll(HttpLogFormatter.responseMetadataUnavailable(this.format, method, uri));
            return null;
        }
        logAll(HttpLogFormatter.response(this.format, HttpLoggingSide.CLIENT, method, uri, status, null,
                durationMillis,
                this.level.includesHeaders() ? HttpLoggingSupport.describeHeaders(response.getHeaders(), this.includedHeaders) : null,
                null));
        return status;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong() - startedAt);
    }

    private static final class LoggingClientHttpResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;

        private final String method;

        private final String uri;

        private final Integer status;

        private final HttpLoggingFormat format;

        private BodyLoggingInputStream body;

        private LoggingClientHttpResponse(HttpLoggingFormat format, ClientHttpResponse delegate, String method,
                String uri, Integer status) {
            this.delegate = delegate;
            this.method = method;
            this.uri = uri;
            this.status = status;
            this.format = format;
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
                this.body = new BodyLoggingInputStream(this.format, this.delegate.getBody(),
                        this.delegate.getHeaders(), this.method, this.uri, this.status);
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
            } else {
                logAll(HttpLogFormatter.responseBody(this.format, HttpLoggingSide.CLIENT, this.method, this.uri,
                        responseStatus(this.status),
                        HttpLoggingSupport.describeBody(this.delegate.getHeaders().getContentType(),
                                new byte[0], false, false)));
            }
            this.delegate.close();
        }

        private void logUnconsumedErrorBody() {
            try {
                if (this.body == null && this.delegate.getStatusCode().isError()) {
                    this.body = new BodyLoggingInputStream(this.format, this.delegate.getBody(),
                            this.delegate.getHeaders(), this.method, this.uri, this.status);
                    this.body.readNBytes(HttpLoggingSupport.MAX_BODY_BYTES + 1);
                }
            } catch (IOException | RuntimeException exception) {
                logAll(HttpLogFormatter.responseBodyUnavailable(this.format, this.method, this.uri,
                        responseStatus(this.status)));
            }
        }
    }

    private static final class BodyLoggingInputStream extends FilterInputStream {

        private final HttpHeaders headers;

        private final String method;

        private final String uri;

        private final Integer status;

        private final HttpLoggingFormat format;

        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private boolean complete;

        private boolean truncated;

        private boolean skipped;

        private boolean logged;

        private int capturedAtMark = -1;

        private BodyLoggingInputStream(HttpLoggingFormat format, InputStream delegate, HttpHeaders headers,
                String method, String uri, Integer status) {
            super(delegate);
            this.headers = headers;
            this.method = method;
            this.uri = uri;
            this.status = status;
            this.format = format;
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
            var remaining = HttpLoggingSupport.MAX_BODY_BYTES - this.captured.size();
            if (remaining <= 0) {
                this.truncated = true;
                return;
            }
            var capturedLength = Math.min(remaining, length);
            this.captured.write(bytes, offset, capturedLength);
            this.truncated = capturedLength < length;
        }

        private void capture(int value) {
            if (this.captured.size() < HttpLoggingSupport.MAX_BODY_BYTES) {
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
            logAll(HttpLogFormatter.responseBody(this.format, HttpLoggingSide.CLIENT, this.method, this.uri,
                    responseStatus(this.status),
                    HttpLoggingSupport.describeBody(this.headers.getContentType(), this.captured.toByteArray(),
                            this.complete && !this.skipped, this.truncated)));
        }
    }

    private static Object responseStatus(Integer status) {
        return status != null ? status : "<unavailable>";
    }

    private static void logAll(java.util.List<String> messages) {
        for (var message : messages) {
            safely(() -> LOG.info("{}", message));
        }
    }

    private static void safely(Runnable logging) {
        try {
            logging.run();
        } catch (RuntimeException exception) {
            // Diagnostic logging must not change HTTP processing.
        }
    }
}
