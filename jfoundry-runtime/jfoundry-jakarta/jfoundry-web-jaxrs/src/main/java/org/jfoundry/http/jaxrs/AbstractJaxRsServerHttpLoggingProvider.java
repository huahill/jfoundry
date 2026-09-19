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
import org.jfoundry.http.HttpLogFormatter;
import org.jfoundry.http.HttpLoggingFormat;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;
import org.jfoundry.http.HttpLoggingSide;

/// Shared server-side JAX-RS HTTP logging provider for Jakarta-based runtimes.
public abstract class AbstractJaxRsServerHttpLoggingProvider extends AbstractJaxRsHttpLoggingSupport
        implements ContainerRequestFilter, ContainerResponseFilter, ReaderInterceptor, WriterInterceptor {

    private static final String SERVER_STATE = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".STATE";
    private static final String REQUEST_BODY = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".REQUEST_BODY";
    private static final String RESPONSE_BODY = AbstractJaxRsServerHttpLoggingProvider.class.getName() + ".RESPONSE_BODY";

    private final String loggingLevel;

    private final String loggingFormat;

    private final String loggingIncludedHeaders;

    /// Creates the shared server provider implementation.
    protected AbstractJaxRsServerHttpLoggingProvider(
            String loggingLevel,
            String loggingFormat,
            String loggingIncludedHeaders,
            BooleanSupplier infoEnabled,
            LongSupplier nanoTime,
            InfoLogger logger) {
        super(infoEnabled, nanoTime, logger);
        this.loggingLevel = java.util.Objects.requireNonNull(loggingLevel, "loggingLevel must not be null");
        this.loggingFormat = java.util.Objects.requireNonNull(loggingFormat, "loggingFormat must not be null");
        this.loggingIncludedHeaders = java.util.Objects.requireNonNull(loggingIncludedHeaders,
                "loggingIncludedHeaders must not be null");
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
        var format = configuredFormat(this.loggingFormat);
        var includedHeaders = configuredIncludedHeaders(this.loggingIncludedHeaders);
        var state = new ServerState(request.getMethod(), HttpLoggingPolicy.withoutQuery(
                request.getUriInfo().getRequestUri()), level, format, includedHeaders, nanoTime());
        request.setProperty(SERVER_STATE, state);
        logAll(HttpLogFormatter.request(format, HttpLoggingSide.SERVER, state.method(), state.uri(),
                level.includesHeaders()
                        ? HttpLoggingPolicy.describeHeaders(request.getHeaders(), includedHeaders) : null));
        if (level.includesBodies()) {
            var body = bodyLog(request.getMediaType(), description -> logAll(HttpLogFormatter.requestBody(
                    format, HttpLoggingSide.SERVER, state.method(), state.uri(), description)));
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
        logAll(HttpLogFormatter.response(state.format(), HttpLoggingSide.SERVER, state.method(), state.uri(),
                response.getStatus(), null, elapsedMillis(state.startedAt()),
                state.level().includesHeaders()
                        ? HttpLoggingPolicy.describeHeaders(response.getStringHeaders(), state.includedHeaders())
                        : null,
                null));
        if (state.level().includesBodies()) {
            var body = bodyLog(response.getMediaType(), description -> logAll(HttpLogFormatter.responseBody(
                    state.format(), HttpLoggingSide.SERVER, state.method(), state.uri(), response.getStatus(),
                    description)));
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

    private record ServerState(String method, String uri, HttpLoggingLevel level, HttpLoggingFormat format,
            java.util.List<String> includedHeaders, long startedAt) {
    }

    private void logAll(java.util.List<String> messages) {
        for (var message : messages) {
            safely(() -> info(message));
        }
    }
}
