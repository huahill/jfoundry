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
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.HttpLoggingPolicy;

/// Shared outbound JAX-RS HTTP logging provider for Jakarta-based runtimes.
public abstract class AbstractJaxRsRestClientLoggingProvider extends AbstractJaxRsHttpLoggingSupport
        implements ClientRequestFilter, ClientResponseFilter, ReaderInterceptor, WriterInterceptor {

    /// Configuration key for outbound MicroProfile REST Client logging.
    public static final String LOGGING_LEVEL = "jfoundry.web.rest-client.logging-level";

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
        var state = new ClientState(request.getMethod(), HttpLoggingPolicy.withoutQuery(request.getUri()),
                level, nanoTime());
        request.setProperty(CLIENT_STATE, state);
        safely(() -> info("HTTP client request: method={0}, uri={1}", state.method(), state.uri()));
        if (level.includesHeaders()) {
            safely(() -> info("HTTP client request headers: method={0}, uri={1}, headers={2}",
                    state.method(), state.uri(), HttpLoggingPolicy.describeHeaders(request.getStringHeaders())));
        }
        if (level.includesBodies()) {
            var body = bodyLog(request.getMediaType(), description -> info(
                    "HTTP client request body: method={0}, uri={1}, body={2}",
                    state.method(), state.uri(), description));
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
        safely(() -> info("HTTP client response: method={0}, uri={1}, status={2}, duration={3}ms",
                state.method(), state.uri(), response.getStatus(), elapsedMillis(state.startedAt())));
        if (state.level().includesHeaders()) {
            safely(() -> info("HTTP client response headers: method={0}, uri={1}, status={2}, headers={3}",
                    state.method(), state.uri(), response.getStatus(),
                    HttpLoggingPolicy.describeHeaders(response.getHeaders())));
        }
        if (state.level().includesBodies()) {
            var body = bodyLog(response.getMediaType(), description -> info(
                    "HTTP client response body: method={0}, uri={1}, status={2}, body={3}",
                    state.method(), state.uri(), response.getStatus(), description));
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

    private record ClientState(String method, String uri, HttpLoggingLevel level, long startedAt) {
    }
}
