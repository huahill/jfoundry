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

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jfoundry.http.HttpLoggingLevel;

/// Shared mechanics for server and client JAX-RS HTTP logging providers.
///
/// Runtime adapters own provider registration, configuration keys, and logging-facade integration.
public abstract class AbstractJaxRsHttpLoggingSupport {

    private final BooleanSupplier infoEnabled;

    private final InfoLogger logger;

    private final LongSupplier nanoTime;

    /// Creates the shared logging support.
    protected AbstractJaxRsHttpLoggingSupport(
            BooleanSupplier infoEnabled,
            LongSupplier nanoTime,
            InfoLogger logger) {
        this.infoEnabled = Objects.requireNonNull(infoEnabled, "infoEnabled must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    protected final boolean isInfoEnabled() {
        return this.infoEnabled.getAsBoolean();
    }

    protected final HttpLoggingLevel configuredLevel(String name) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(name, HttpLoggingLevel.class)
                    .orElse(HttpLoggingLevel.NONE);
        } catch (RuntimeException exception) {
            safely(() -> info("HTTP logging configuration could not be read: property={0}", name));
            return HttpLoggingLevel.NONE;
        }
    }

    protected final long nanoTime() {
        return this.nanoTime.getAsLong();
    }

    protected final long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong() - startedAt);
    }

    protected final void info(String message, Object... arguments) {
        this.logger.info(message, arguments);
    }

    protected final void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // Diagnostic logging must not alter HTTP processing.
        }
    }

    protected final BodyLog bodyLog(MediaType mediaType, Consumer<String> logger) {
        return new BodyLog(capture -> logger.accept(JaxRsHttpLoggingSupport.describeBody(mediaType, capture)));
    }

    protected final void completeAndLog(BodyLog body) {
        body.completeAndLog();
    }

    protected final InputStream capturingInputStream(InputStream delegate, BodyLog body) {
        return new CapturingInputStream(delegate, body);
    }

    protected final Object aroundReadFrom(ReaderInterceptorContext context, String propertyName) throws IOException {
        var body = (BodyLog) context.getProperty(propertyName);
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

    protected final void aroundWriteTo(WriterInterceptorContext context, String propertyName) throws IOException {
        var body = (BodyLog) context.getProperty(propertyName);
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

    /// Runtime-specific bridge for info logging with {@link java.text.MessageFormat} placeholders.
    @FunctionalInterface
    public interface InfoLogger {

        /// Writes one info message without affecting HTTP processing when the backend fails.
        void info(String message, Object... arguments);
    }

    protected static final class BodyLog {

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
                try {
                    this.logger.accept(this.capture);
                } catch (RuntimeException ignored) {
                    // Diagnostic logging must not alter HTTP processing.
                }
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
