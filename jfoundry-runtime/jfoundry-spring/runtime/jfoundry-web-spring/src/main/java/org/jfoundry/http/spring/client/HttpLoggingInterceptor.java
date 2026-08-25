package org.jfoundry.http.spring.client;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.jfoundry.http.HttpLoggingLevel;
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
/// Logging is inactive unless this type's logger is enabled at `DEBUG`. `BASIC` and `HEADERS` never access or
/// wrap bodies. `FULL` captures size-limited JSON bodies and may read an unconsumed error response on close.
/// `durationMs` ends when response headers are available and excludes response-body consumption and decoding.
public final class HttpLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingInterceptor.class);

    private final HttpLoggingLevel level;

    private final BooleanSupplier debugEnabled;

    private final LongSupplier nanoTime;

    /// Creates an interceptor with the requested logging detail.
    public HttpLoggingInterceptor(HttpLoggingLevel level) {
        this(level, LOG::isDebugEnabled, System::nanoTime);
    }

    HttpLoggingInterceptor(HttpLoggingLevel level, BooleanSupplier debugEnabled, LongSupplier nanoTime) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.debugEnabled = Objects.requireNonNull(debugEnabled, "debugEnabled must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (this.level == HttpLoggingLevel.NONE || !this.debugEnabled.getAsBoolean()) {
            return execution.execute(request, body);
        }
        safely(() -> logRequest(request, body));
        var startedAt = this.nanoTime.getAsLong();
        try {
            var response = execution.execute(request, body);
            logResponseSafely(response, elapsedMillis(startedAt));
            return this.level.includesBodies() ? new LoggingClientHttpResponse(response) : response;
        } catch (IOException | RuntimeException exception) {
            safely(() -> LOG.debug("HTTP client request failed: method={}, uri={}, exception={}, durationMs={}",
                    request.getMethod(), HttpLoggingSupport.withoutQuery(request.getURI()),
                    exception.getClass().getName(), elapsedMillis(startedAt)));
            throw exception;
        }
    }

    private void logRequest(HttpRequest request, byte[] body) {
        if (this.level.includesBodies()) {
            LOG.debug("HTTP client request: method={}, uri={}, headers={}, body={}", request.getMethod(),
                    HttpLoggingSupport.withoutQuery(request.getURI()),
                    HttpLoggingSupport.describeHeaders(request.getHeaders()),
                    HttpLoggingSupport.describeBody(request.getHeaders().getContentType(), body, true, false));
        } else if (this.level.includesHeaders()) {
            LOG.debug("HTTP client request: method={}, uri={}, headers={}", request.getMethod(),
                    HttpLoggingSupport.withoutQuery(request.getURI()),
                    HttpLoggingSupport.describeHeaders(request.getHeaders()));
        } else {
            LOG.debug("HTTP client request: method={}, uri={}", request.getMethod(),
                    HttpLoggingSupport.withoutQuery(request.getURI()));
        }
    }

    private void logResponse(ClientHttpResponse response, long durationMs) throws IOException {
        if (this.level.includesHeaders()) {
            LOG.debug("HTTP client response: status={}, headers={}, durationMs={}", response.getStatusCode(),
                    HttpLoggingSupport.describeHeaders(response.getHeaders()), durationMs);
        } else {
            LOG.debug("HTTP client response: status={}, durationMs={}", response.getStatusCode(), durationMs);
        }
    }

    private void logResponseSafely(ClientHttpResponse response, long durationMs) {
        try {
            logResponse(response, durationMs);
        } catch (IOException | RuntimeException exception) {
            safely(() -> LOG.debug("HTTP client response metadata could not be read for logging"));
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong() - startedAt);
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
                    this.body.readNBytes(HttpLoggingSupport.MAX_BODY_BYTES + 1);
                }
            } catch (IOException | RuntimeException exception) {
                safely(() -> LOG.debug("HTTP client response body could not be read for logging"));
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
            safely(() -> LOG.debug("HTTP client response body: {}", HttpLoggingSupport.describeBody(
                    this.headers.getContentType(), this.captured.toByteArray(),
                    this.complete && !this.skipped, this.truncated)));
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
