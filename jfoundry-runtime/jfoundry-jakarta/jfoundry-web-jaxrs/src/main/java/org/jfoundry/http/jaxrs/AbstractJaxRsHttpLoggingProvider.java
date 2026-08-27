package org.jfoundry.http.jaxrs;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;

/// Shared JAX-RS HTTP logging implementation for Jakarta-based runtimes.
///
/// Runtime adapters own provider registration, configuration keys, and logging-facade integration.
public abstract class AbstractJaxRsHttpLoggingProvider implements ContainerRequestFilter, ContainerResponseFilter,
        ClientRequestFilter, ClientResponseFilter, ReaderInterceptor, WriterInterceptor {

    /// Configuration key for outbound MicroProfile REST Client logging.
    public static final String CLIENT_LOGGING_LEVEL = "jfoundry.web.rest-client.logging-level";

    private static final String SERVER_STATE = AbstractJaxRsHttpLoggingProvider.class.getName() + ".SERVER_STATE";

    private static final String SERVER_REQUEST_BODY =
            AbstractJaxRsHttpLoggingProvider.class.getName() + ".SERVER_REQUEST_BODY";

    private static final String SERVER_RESPONSE_BODY =
            AbstractJaxRsHttpLoggingProvider.class.getName() + ".SERVER_RESPONSE_BODY";

    private static final String CLIENT_STATE = AbstractJaxRsHttpLoggingProvider.class.getName() + ".CLIENT_STATE";

    private static final String CLIENT_REQUEST_BODY =
            AbstractJaxRsHttpLoggingProvider.class.getName() + ".CLIENT_REQUEST_BODY";

    private static final String CLIENT_RESPONSE_BODY =
            AbstractJaxRsHttpLoggingProvider.class.getName() + ".CLIENT_RESPONSE_BODY";

    private final String serverLoggingLevel;

    private final BooleanSupplier infoEnabled;

    private final InfoLogger logger;

    private final LongSupplier nanoTime;

    /// Creates the shared provider implementation.
    protected AbstractJaxRsHttpLoggingProvider(
            String serverLoggingLevel,
            BooleanSupplier infoEnabled,
            LongSupplier nanoTime,
            InfoLogger logger) {
        this.serverLoggingLevel = Objects.requireNonNull(serverLoggingLevel, "serverLoggingLevel must not be null");
        this.infoEnabled = Objects.requireNonNull(infoEnabled, "infoEnabled must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    /// Creates the shared provider with the former debug-named logging bridge.
    ///
    /// @deprecated since 1.4; use the constructor accepting {@link InfoLogger}.
    @Deprecated(since = "1.4")
    protected AbstractJaxRsHttpLoggingProvider(
            String serverLoggingLevel,
            BooleanSupplier enabled,
            DebugLogger logger,
            LongSupplier nanoTime) {
        this(serverLoggingLevel, enabled, nanoTime, logger::debug);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!this.infoEnabled.getAsBoolean()) {
            return;
        }
        var level = configuredLevel(this.serverLoggingLevel, HttpLoggingLevel.NONE);
        if (level == HttpLoggingLevel.NONE) {
            return;
        }
        var state = new ServerState(request.getMethod(), HttpLoggingPolicy.withoutQuery(
                request.getUriInfo().getRequestUri()), level, this.nanoTime.getAsLong());
        request.setProperty(SERVER_STATE, state);
        logServerRequest(request, state);
        if (level.includesBodies()) {
            var body = bodyLog(captured -> info(
                    "HTTP server request body: method={0}, uri={1}, body={2}", state.method(), state.uri(),
                    JaxRsHttpLoggingSupport.describeBody(request.getMediaType(), captured)));
            request.setProperty(SERVER_REQUEST_BODY, body);
            if (!request.hasEntity()) {
                body.completeAndLog();
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var state = (ServerState) request.getProperty(SERVER_STATE);
        if (state == null) {
            return;
        }
        safely(() -> info("HTTP server response: method={0}, uri={1}, status={2}, duration={3}ms",
                state.method(), state.uri(), response.getStatus(), elapsedMillis(state.startedAt())));
        if (state.level().includesHeaders()) {
            safely(() -> info("HTTP server response headers: method={0}, uri={1}, status={2}, headers={3}",
                    state.method(), state.uri(), response.getStatus(),
                    HttpLoggingPolicy.describeHeaders(response.getStringHeaders())));
        }
        if (state.level().includesBodies()) {
            var body = bodyLog(captured -> info(
                    "HTTP server response body: method={0}, uri={1}, status={2}, body={3}", state.method(), state.uri(),
                    response.getStatus(), JaxRsHttpLoggingSupport.describeBody(response.getMediaType(), captured)));
            request.setProperty(SERVER_RESPONSE_BODY, body);
            if (!response.hasEntity()) {
                body.completeAndLog();
            }
        }
    }

    @Override
    public void filter(ClientRequestContext request) {
        if (!this.infoEnabled.getAsBoolean()) {
            return;
        }
        var level = configuredLevel(CLIENT_LOGGING_LEVEL, HttpLoggingLevel.NONE);
        if (level == HttpLoggingLevel.NONE) {
            return;
        }
        var state = new ClientState(request.getMethod(), HttpLoggingPolicy.withoutQuery(request.getUri()),
                level, this.nanoTime.getAsLong());
        request.setProperty(CLIENT_STATE, state);
        safely(() -> info("HTTP client request: method={0}, uri={1}", state.method(), state.uri()));
        if (level.includesHeaders()) {
            safely(() -> info("HTTP client request headers: method={0}, uri={1}, headers={2}",
                    state.method(), state.uri(), HttpLoggingPolicy.describeHeaders(request.getStringHeaders())));
        }
        if (level.includesBodies()) {
            var body = bodyLog(captured -> info(
                    "HTTP client request body: method={0}, uri={1}, body={2}", state.method(), state.uri(),
                    JaxRsHttpLoggingSupport.describeBody(request.getMediaType(), captured)));
            request.setProperty(CLIENT_REQUEST_BODY, body);
            if (!request.hasEntity()) {
                body.completeAndLog();
            }
        }
    }

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        var state = (ClientState) request.getProperty(CLIENT_STATE);
        if (state == null) {
            return;
        }
        safely(() -> info("HTTP client response: method={0}, uri={1}, status={2}, duration={3}ms",
                state.method(), state.uri(), response.getStatus(), elapsedMillis(state.startedAt())));
        if (state.level().includesHeaders()) {
            safely(() -> info("HTTP client response headers: method={0}, uri={1}, status={2}, headers={3}",
                    state.method(), state.uri(), response.getStatus(),
                    HttpLoggingPolicy.describeHeaders(response.getHeaders())));
        }
        if (state.level().includesBodies()) {
            var body = bodyLog(captured -> info(
                    "HTTP client response body: method={0}, uri={1}, status={2}, body={3}",
                    state.method(), state.uri(), response.getStatus(),
                    JaxRsHttpLoggingSupport.describeBody(response.getMediaType(), captured)));
            if (response.hasEntity()) {
                request.setProperty(CLIENT_RESPONSE_BODY, body);
                response.setEntityStream(new CapturingInputStream(response.getEntityStream(), body));
            } else {
                body.completeAndLog();
            }
        }
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        var body = (BodyLog) context.getProperty(SERVER_REQUEST_BODY);
        if (body == null) {
            body = (BodyLog) context.getProperty(CLIENT_RESPONSE_BODY);
        }
        if (body == null) {
            return context.proceed();
        }
        body.beginReader();
        var original = context.getInputStream();
        context.setInputStream(new CapturingInputStream(original, body));
        try {
            var entity = context.proceed();
            body.completeAndLog();
            return entity;
        } finally {
            context.setInputStream(original);
            body.endReader();
            body.log();
        }
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        var body = (BodyLog) context.getProperty(CLIENT_REQUEST_BODY);
        if (body == null) {
            body = (BodyLog) context.getProperty(SERVER_RESPONSE_BODY);
        }
        if (body == null) {
            context.proceed();
            return;
        }
        var original = context.getOutputStream();
        context.setOutputStream(new CapturingOutputStream(original, body));
        try {
            context.proceed();
            body.completeAndLog();
        } finally {
            context.setOutputStream(original);
            body.log();
        }
    }

    private void logServerRequest(ContainerRequestContext request, ServerState state) {
        safely(() -> info("HTTP server request: method={0}, uri={1}", state.method(), state.uri()));
        if (state.level().includesHeaders()) {
            safely(() -> info("HTTP server request headers: method={0}, uri={1}, headers={2}",
                    state.method(), state.uri(), HttpLoggingPolicy.describeHeaders(request.getHeaders())));
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong() - startedAt);
    }

    private HttpLoggingLevel configuredLevel(String name, HttpLoggingLevel defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(name, HttpLoggingLevel.class).orElse(defaultValue);
        } catch (RuntimeException exception) {
            safely(() -> info("HTTP logging configuration could not be read: property={0}", name));
            return defaultValue;
        }
    }

    private void info(String message, Object... arguments) {
        this.logger.info(message, arguments);
    }

    private static BodyLog bodyLog(Consumer<JaxRsHttpLoggingSupport.BodyCapture> logger) {
        return new BodyLog(logger);
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Diagnostic logging must not alter HTTP processing.
        }
    }

    /// Runtime-specific bridge for info logging with {@link java.text.MessageFormat} placeholders.
    @FunctionalInterface
    public interface InfoLogger {

        /// Writes one info message without affecting HTTP processing when the backend fails.
        void info(String message, Object... arguments);
    }

    /// Former runtime-specific bridge for debug logging.
    ///
    /// @deprecated since 1.4; use {@link InfoLogger}.
    @Deprecated(since = "1.4")
    @FunctionalInterface
    public interface DebugLogger {

        /// Writes one debug message.
        void debug(String message, Object... arguments);
    }

    private record ServerState(String method, String uri, HttpLoggingLevel level, long startedAt) {
    }

    private record ClientState(String method, String uri, HttpLoggingLevel level, long startedAt) {
    }

    private static final class BodyLog {

        private final JaxRsHttpLoggingSupport.BodyCapture capture = new JaxRsHttpLoggingSupport.BodyCapture();

        private final Consumer<JaxRsHttpLoggingSupport.BodyCapture> logger;

        private final AtomicBoolean logged = new AtomicBoolean();

        private boolean readerActive;

        private BodyLog(Consumer<JaxRsHttpLoggingSupport.BodyCapture> logger) {
            this.logger = logger;
        }

        private void capture(int value) {
            this.capture.capture(value);
        }

        private void capture(byte[] source, int offset, int length) {
            this.capture.capture(source, offset, length);
        }

        private void completeAndLog() {
            this.capture.markComplete();
            log();
        }

        private synchronized void beginReader() {
            this.readerActive = true;
        }

        private synchronized void endReader() {
            this.readerActive = false;
        }

        private synchronized void logOnClose() {
            if (!this.readerActive) {
                log();
            }
        }

        private void log() {
            if (this.logged.compareAndSet(false, true)) {
                safely(() -> this.logger.accept(this.capture));
            }
        }
    }

    private static final class CapturingInputStream extends FilterInputStream {

        private final BodyLog body;

        private CapturingInputStream(InputStream delegate, BodyLog body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public int read() throws IOException {
            var value = super.read();
            if (value == -1) {
                this.body.completeAndLog();
            } else {
                this.body.capture(value);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            var read = super.read(bytes, offset, length);
            if (read == -1) {
                this.body.completeAndLog();
            } else if (read > 0) {
                this.body.capture(bytes, offset, read);
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                this.body.logOnClose();
            }
        }
    }

    private static final class CapturingOutputStream extends FilterOutputStream {

        private final BodyLog body;

        private CapturingOutputStream(OutputStream delegate, BodyLog body) {
            super(delegate);
            this.body = body;
        }

        @Override
        public void write(int value) throws IOException {
            this.out.write(value);
            this.body.capture(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            this.out.write(bytes, offset, length);
            this.body.capture(bytes, offset, length);
        }
    }
}
