package org.jfoundry.web.spring.filter;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.spring.HttpLoggingSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.filter.OncePerRequestFilter;

/// Records inbound Servlet HTTP activity without delaying or replacing application I/O.
///
/// Logging is inactive unless this type's logger is enabled at `INFO`. `FULL` uses tee wrappers that forward
/// bytes immediately and retain no more than 8 KiB. Synchronous duration ends when the filter chain returns;
/// asynchronous duration ends on terminal complete, error, or timeout and does not measure client receipt.
public final class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingFilter.class);

    private static final String STATE_ATTRIBUTE = HttpLoggingFilter.class.getName() + ".STATE";

    private final HttpLoggingLevel level;

    private final Predicate<HttpServletRequest> excludedRequest;

    private final BooleanSupplier infoEnabled;

    private final LongSupplier nanoTime;

    /// Creates a filter with the requested logging detail.
    public HttpLoggingFilter(HttpLoggingLevel level) {
        this(level, request -> false, LOG::isInfoEnabled, System::nanoTime);
    }

    /// Creates a filter with the requested logging detail and request exclusion predicate.
    public HttpLoggingFilter(HttpLoggingLevel level, Predicate<HttpServletRequest> excludedRequest) {
        this(level, excludedRequest, LOG::isInfoEnabled, System::nanoTime);
    }

    HttpLoggingFilter(HttpLoggingLevel level, BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        this(level, request -> false, infoEnabled, nanoTime);
    }

    HttpLoggingFilter(HttpLoggingLevel level, Predicate<HttpServletRequest> excludedRequest,
            BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.excludedRequest = Objects.requireNonNull(excludedRequest, "excludedRequest must not be null");
        this.infoEnabled = Objects.requireNonNull(infoEnabled, "infoEnabled must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return this.excludedRequest.test(request);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        doFilterInternal(request, response, filterChain);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (this.level == HttpLoggingLevel.NONE || !this.infoEnabled.getAsBoolean()) {
            filterChain.doFilter(request, response);
            return;
        }

        var state = (RequestState) request.getAttribute(STATE_ATTRIBUTE);
        if (state == null) {
            state = new RequestState(request, response, this.level, this.nanoTime);
            request.setAttribute(STATE_ATTRIBUTE, state);
            state.logRequest(request);
        }

        var filteredRequest = this.level.includesBodies() && !(request instanceof CapturingRequest)
                ? new CapturingRequest(request, state.requestBody) : request;
        var filteredResponse = this.level.includesBodies() && !(response instanceof CapturingResponse)
                ? new CapturingResponse(response, state.responseBody) : response;
        try {
            filterChain.doFilter(filteredRequest, filteredResponse);
        } catch (IOException | ServletException | RuntimeException exception) {
            state.logFailure(exception, "failed");
            throw exception;
        }

        if (filteredRequest.isAsyncStarted()) {
            state.listen(filteredRequest.getAsyncContext(), filteredRequest, filteredResponse);
        } else {
            state.logResponse("complete");
        }
    }

    private static final class RequestState {

        private final String method;

        private final String uri;

        private final HttpServletResponse response;

        private final String requestContentType;

        private final HttpLoggingLevel level;

        private final LongSupplier nanoTime;

        private final long startedAt;

        private final BodyCapture requestBody;

        private final BodyCapture responseBody = new BodyCapture(false);

        private final AtomicBoolean terminal = new AtomicBoolean();

        private final Set<AsyncContext> listening = Collections.synchronizedSet(
                Collections.newSetFromMap(new IdentityHashMap<>()));

        private RequestState(HttpServletRequest request, HttpServletResponse response, HttpLoggingLevel level,
                LongSupplier nanoTime) {
            this.method = request.getMethod();
            this.uri = requestUri(request);
            this.response = response;
            this.requestContentType = request.getContentType();
            this.level = level;
            this.nanoTime = nanoTime;
            this.startedAt = nanoTime.getAsLong();
            this.requestBody = new BodyCapture(request.getContentLengthLong() <= 0);
        }

        private void logRequest(HttpServletRequest request) {
            safely(() -> LOG.info("HTTP server request: method={}, uri={}", this.method, this.uri));
            if (this.level.includesHeaders()) {
                safely(() -> LOG.info("HTTP server request headers: method={}, uri={}, headers={}",
                        this.method, this.uri, HttpLoggingSupport.describeHeaders(requestHeaders(request))));
            }
        }

        private void logResponse(String completion) {
            if (!this.terminal.compareAndSet(false, true)) {
                return;
            }
            this.responseBody.markComplete();
            logRequestBody();
            var status = this.response.getStatus();
            safely(() -> LOG.info(
                    "HTTP server response: method={}, uri={}, status={}, completion={}, duration={}ms",
                    this.method, this.uri, status, completion, elapsedMillis()));
            if (this.level.includesHeaders()) {
                safely(() -> LOG.info("HTTP server response headers: method={}, uri={}, status={}, headers={}",
                        this.method, this.uri, status,
                        HttpLoggingSupport.describeHeaders(responseHeaders(this.response))));
            }
            if (this.level.includesBodies()) {
                safely(() -> LOG.info("HTTP server response body: method={}, uri={}, status={}, body={}",
                        this.method, this.uri, status,
                        HttpLoggingSupport.describeBody(this.response.getContentType(),
                                this.responseBody.bytes(), this.responseBody.complete(), this.responseBody.truncated())));
            }
        }

        private void logFailure(Throwable exception, String completion) {
            if (!this.terminal.compareAndSet(false, true)) {
                return;
            }
            logRequestBody();
            safely(() -> LOG.info(
                    "HTTP server request failed: method={}, uri={}, completion={}, exception={}, duration={}ms",
                    this.method, this.uri, completion, exception.getClass().getName(), elapsedMillis()));
        }

        private void logAsyncFailure(Throwable exception, String completion) {
            if (exception == null) {
                if (!this.terminal.compareAndSet(false, true)) {
                    return;
                }
                logRequestBody();
                safely(() -> LOG.info(
                        "HTTP server request failed: method={}, uri={}, completion={}, duration={}ms",
                        this.method, this.uri, completion, elapsedMillis()));
            } else {
                logFailure(exception, completion);
            }
        }

        private void logRequestBody() {
            if (this.level.includesBodies()) {
                safely(() -> LOG.info("HTTP server request body: method={}, uri={}, body={}",
                        this.method, this.uri, HttpLoggingSupport.describeBody(this.requestContentType,
                                this.requestBody.bytes(), this.requestBody.complete(), this.requestBody.truncated())));
            }
        }

        private void listen(AsyncContext asyncContext, HttpServletRequest request, HttpServletResponse response) {
            if (!this.listening.add(asyncContext)) {
                return;
            }
            try {
                asyncContext.addListener(new TerminalAsyncListener(this), request, response);
            } catch (IllegalStateException exception) {
                logResponse("complete");
            }
        }

        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong() - this.startedAt);
        }

    }

    private static final class TerminalAsyncListener implements AsyncListener {

        private final RequestState state;

        private TerminalAsyncListener(RequestState state) {
            this.state = state;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            this.state.logResponse("async-complete");
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            this.state.logAsyncFailure(event.getThrowable(), "async-timeout");
        }

        @Override
        public void onError(AsyncEvent event) {
            this.state.logAsyncFailure(event.getThrowable(), "async-error");
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            var request = event.getSuppliedRequest() != null ? event.getSuppliedRequest()
                    : event.getAsyncContext().getRequest();
            var response = event.getSuppliedResponse() != null ? event.getSuppliedResponse()
                    : event.getAsyncContext().getResponse();
            this.state.listen(event.getAsyncContext(), (HttpServletRequest) request, (HttpServletResponse) response);
        }
    }

    private static final class CapturingRequest extends HttpServletRequestWrapper {

        private final BodyCapture capture;

        private ServletInputStream inputStream;

        private BufferedReader reader;

        private CapturingRequest(HttpServletRequest request, BodyCapture capture) {
            super(request);
            this.capture = capture;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (this.reader != null) {
                throw new IllegalStateException("getReader() has already been called");
            }
            if (this.inputStream == null) {
                this.inputStream = new CapturingServletInputStream(super.getInputStream(), this.capture);
            }
            return this.inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (this.inputStream != null && this.reader == null) {
                throw new IllegalStateException("getInputStream() has already been called");
            }
            if (this.reader == null) {
                this.inputStream = new CapturingServletInputStream(super.getInputStream(), this.capture);
                this.reader = new BufferedReader(new InputStreamReader(this.inputStream,
                        requestCharset(getCharacterEncoding())));
            }
            return this.reader;
        }
    }

    private static final class CapturingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;

        private final BodyCapture capture;

        private CapturingServletInputStream(ServletInputStream delegate, BodyCapture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public int read() throws IOException {
            var value = this.delegate.read();
            if (value == -1) {
                this.capture.markComplete();
            } else {
                this.capture.write(value);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            var read = this.delegate.read(bytes, offset, length);
            if (read == -1) {
                this.capture.markComplete();
            } else if (read > 0) {
                this.capture.write(bytes, offset, read);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            return this.delegate.skip(count);
        }

        @Override
        public boolean isFinished() {
            return this.delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return this.delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            this.delegate.setReadListener(readListener);
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }
    }

    private static final class CapturingResponse extends HttpServletResponseWrapper {

        private final BodyCapture capture;

        private ServletOutputStream outputStream;

        private PrintWriter writer;

        private CapturingResponse(HttpServletResponse response, BodyCapture capture) {
            super(response);
            this.capture = capture;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (this.writer != null) {
                throw new IllegalStateException("getWriter() has already been called");
            }
            if (this.outputStream == null) {
                this.outputStream = new CapturingServletOutputStream(super.getOutputStream(), this.capture);
            }
            return this.outputStream;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (this.outputStream != null && this.writer == null) {
                throw new IllegalStateException("getOutputStream() has already been called");
            }
            if (this.writer == null) {
                this.outputStream = new CapturingServletOutputStream(super.getOutputStream(), this.capture);
                this.writer = new PrintWriter(new OutputStreamWriter(this.outputStream,
                        Charset.forName(getCharacterEncoding())));
            }
            return this.writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (this.writer != null) {
                this.writer.flush();
            } else if (this.outputStream != null) {
                this.outputStream.flush();
            }
            super.flushBuffer();
        }

        @Override
        public void resetBuffer() {
            super.resetBuffer();
            this.capture.reset();
        }

        @Override
        public void reset() {
            super.reset();
            this.capture.reset();
        }

        @Override
        public void sendError(int status) throws IOException {
            this.capture.reset();
            super.sendError(status);
        }

        @Override
        public void sendError(int status, String message) throws IOException {
            this.capture.reset();
            super.sendError(status, message);
        }
    }

    private static final class CapturingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private final BodyCapture capture;

        private CapturingServletOutputStream(ServletOutputStream delegate, BodyCapture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public void write(int value) throws IOException {
            this.delegate.write(value);
            this.capture.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            this.delegate.write(bytes, offset, length);
            this.capture.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            this.delegate.flush();
        }

        @Override
        public void close() throws IOException {
            this.delegate.close();
        }

        @Override
        public boolean isReady() {
            return this.delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            this.delegate.setWriteListener(writeListener);
        }
    }

    private static final class BodyCapture {

        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        private boolean complete;

        private boolean truncated;

        private BodyCapture(boolean complete) {
            this.complete = complete;
        }

        private synchronized void write(int value) {
            this.complete = false;
            if (this.bytes.size() < HttpLoggingSupport.MAX_BODY_BYTES) {
                this.bytes.write(value);
            } else {
                this.truncated = true;
            }
        }

        private synchronized void write(byte[] source, int offset, int length) {
            if (length > 0) {
                this.complete = false;
            }
            var remaining = HttpLoggingSupport.MAX_BODY_BYTES - this.bytes.size();
            if (remaining <= 0) {
                this.truncated = true;
                return;
            }
            var captured = Math.min(remaining, length);
            this.bytes.write(source, offset, captured);
            this.truncated |= captured < length;
        }

        private synchronized void markComplete() {
            this.complete = true;
        }

        private synchronized void reset() {
            this.bytes.reset();
            this.complete = false;
            this.truncated = false;
        }

        private synchronized byte[] bytes() {
            return this.bytes.toByteArray();
        }

        private synchronized boolean complete() {
            return this.complete;
        }

        private synchronized boolean truncated() {
            return this.truncated;
        }
    }

    private static LinkedMultiValueMap<String, String> requestHeaders(HttpServletRequest request) {
        var headers = new LinkedMultiValueMap<String, String>();
        var names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                var name = names.nextElement();
                headers.put(name, Collections.list(request.getHeaders(name)));
            }
        }
        return headers;
    }

    private static String requestUri(HttpServletRequest request) {
        try {
            return HttpLoggingSupport.withoutQuery(URI.create(request.getRequestURL().toString()));
        } catch (RuntimeException exception) {
            return request.getRequestURI();
        }
    }

    private static Charset requestCharset(String characterEncoding) {
        return characterEncoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(characterEncoding);
    }

    private static LinkedMultiValueMap<String, String> responseHeaders(HttpServletResponse response) {
        var headers = new LinkedMultiValueMap<String, String>();
        response.getHeaderNames().forEach(name -> headers.put(name, response.getHeaders(name).stream().toList()));
        return headers;
    }

    private static void safely(Runnable logging) {
        try {
            logging.run();
        } catch (RuntimeException exception) {
            // Diagnostic logging must not change HTTP processing.
        }
    }
}
