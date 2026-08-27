package org.jfoundry.http.jaxrs;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;

/// Shared server-side JAX-RS HTTP logging provider for Jakarta-based runtimes.
public abstract class AbstractJaxRsServerHttpLoggingProvider extends AbstractJaxRsHttpLoggingSupport
        implements ContainerRequestFilter, ContainerResponseFilter, ReaderInterceptor, WriterInterceptor {

    private static final String SERVER_STATE = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".STATE";
    private static final String REQUEST_BODY = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".REQUEST_BODY";
    private static final String RESPONSE_BODY = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".RESPONSE_BODY";

    private final String loggingLevel;

    /// Creates the shared server provider implementation.
    protected AbstractJaxRsServerHttpLoggingProvider(
            String loggingLevel,
            BooleanSupplier infoEnabled,
            LongSupplier nanoTime,
            InfoLogger logger) {
        super(infoEnabled, nanoTime, logger);
        this.loggingLevel = java.util.Objects.requireNonNull(loggingLevel, "loggingLevel must not be null");
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!isInfoEnabled()) {
            return;
        }
        var level = configuredLevel(this.loggingLevel);
        if (level == HttpLoggingLevel.NONE) {
            return;
        }
        var state = new ServerState(request.getMethod(), HttpLoggingPolicy.withoutQuery(
                request.getUriInfo().getRequestUri()), level, nanoTime());
        request.setProperty(SERVER_STATE, state);
        safely(() -> info("HTTP server request: method={0}, uri={1}", state.method(), state.uri()));
        if (level.includesHeaders()) {
            safely(() -> info("HTTP server request headers: method={0}, uri={1}, headers={2}",
                    state.method(), state.uri(), HttpLoggingPolicy.describeHeaders(request.getHeaders())));
        }
        if (level.includesBodies()) {
            var body = bodyLog(request.getMediaType(), description -> info(
                    "HTTP server request body: method={0}, uri={1}, body={2}",
                    state.method(), state.uri(), description));
            request.setProperty(REQUEST_BODY, body);
            if (!request.hasEntity()) {
                completeAndLog(body);
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
            var body = bodyLog(response.getMediaType(), description -> info(
                    "HTTP server response body: method={0}, uri={1}, status={2}, body={3}",
                    state.method(), state.uri(), response.getStatus(), description));
            request.setProperty(RESPONSE_BODY, body);
            if (!response.hasEntity()) {
                completeAndLog(body);
            }
        }
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        return aroundReadFrom(context, REQUEST_BODY);
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        aroundWriteTo(context, RESPONSE_BODY);
    }

    private record ServerState(String method, String uri, HttpLoggingLevel level, long startedAt) {
    }
}
