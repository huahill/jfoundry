package org.jfoundry.http.jaxrs;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.jfoundry.http.HttpLogFormatter;
import org.jfoundry.http.HttpLoggingFormat;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;
import org.jfoundry.http.HttpLoggingSide;

/// Shared outbound JAX-RS HTTP logging provider for Jakarta-based runtimes.
public abstract class AbstractJaxRsRestClientLoggingProvider extends AbstractJaxRsHttpLoggingSupport
        implements ClientRequestFilter, ClientResponseFilter, ReaderInterceptor, WriterInterceptor {

    /// Configuration key for outbound MicroProfile REST Client logging detail.
    public static final String LOGGING_LEVEL = "jfoundry.web.rest-client.logging.level";

    /// Configuration key for outbound MicroProfile REST Client logging layout.
    public static final String LOGGING_FORMAT = "jfoundry.web.rest-client.logging.format";

    private static final String CLIENT_STATE = AbstractJaxRsRestClientLoggingProvider.class.getName() + ".STATE";
    private static final String REQUEST_BODY = AbstractJaxRsRestClientLoggingProvider.class.getName() + ".REQUEST_BODY";
    private static final String RESPONSE_BODY = AbstractJaxRsRestClientLoggingProvider.class.getName() + ".RESPONSE_BODY";

    /// Creates the shared REST client provider implementation.
    protected AbstractJaxRsRestClientLoggingProvider(
            BooleanSupplier infoEnabled,
            LongSupplier nanoTime,
            InfoLogger logger) {
        super(infoEnabled, nanoTime, logger);
    }

    @Override
    public void filter(ClientRequestContext request) {
        if (!isInfoEnabled()) {
            return;
        }
        var level = configuredLevel(LOGGING_LEVEL);
        if (level == HttpLoggingLevel.NONE) {
            return;
        }
        var format = configuredFormat(LOGGING_FORMAT);
        var state = new ClientState(request.getMethod(), HttpLoggingPolicy.withoutQuery(request.getUri()),
                level, format, nanoTime());
        request.setProperty(CLIENT_STATE, state);
        logAll(HttpLogFormatter.request(format, HttpLoggingSide.CLIENT, state.method(), state.uri(),
                level.includesHeaders() ? HttpLoggingPolicy.describeHeaders(request.getStringHeaders()) : null));
        if (level.includesBodies()) {
            var body = bodyLog(request.getMediaType(), description -> logAll(HttpLogFormatter.requestBody(
                    format, HttpLoggingSide.CLIENT, state.method(), state.uri(), description)));
            request.setProperty(REQUEST_BODY, body);
            if (!request.hasEntity()) {
                completeAndLog(body);
            }
        }
    }

    @Override
    public void filter(ClientRequestContext request, ClientResponseContext response) {
        var state = (ClientState) request.getProperty(CLIENT_STATE);
        if (state == null) {
            return;
        }
        logAll(HttpLogFormatter.response(state.format(), HttpLoggingSide.CLIENT, state.method(), state.uri(),
                response.getStatus(), null, elapsedMillis(state.startedAt()),
                state.level().includesHeaders()
                        ? HttpLoggingPolicy.describeHeaders(response.getHeaders()) : null,
                null));
        if (state.level().includesBodies()) {
            var body = bodyLog(response.getMediaType(), description -> logAll(HttpLogFormatter.responseBody(
                    state.format(), HttpLoggingSide.CLIENT, state.method(), state.uri(), response.getStatus(),
                    description)));
            if (response.hasEntity()) {
                request.setProperty(RESPONSE_BODY, body);
                response.setEntityStream(capturingInputStream(response.getEntityStream(), body));
            } else {
                completeAndLog(body);
            }
        }
    }

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context) throws IOException {
        return aroundReadFrom(context, RESPONSE_BODY);
    }

    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException {
        aroundWriteTo(context, REQUEST_BODY);
    }

    private record ClientState(String method, String uri, HttpLoggingLevel level, HttpLoggingFormat format,
            long startedAt) {
    }

    private void logAll(java.util.List<String> messages) {
        for (var message : messages) {
            safely(() -> info(message));
        }
    }
}
