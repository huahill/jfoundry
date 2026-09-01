package org.jfoundry.http.jaxrs;

import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import org.jfoundry.http.correlation.RequestCorrelationContext;
import org.jfoundry.http.correlation.RequestCorrelationId;
import org.jfoundry.http.correlation.RequestCorrelationOptions;

import java.io.IOException;
import java.util.function.Supplier;

/// Shared request-correlation lifecycle for Jakarta REST runtimes.
public abstract class AbstractJaxRsRequestCorrelationProvider
        implements ContainerRequestFilter, ContainerResponseFilter {

    public static final int PRIORITY = Priorities.USER - 300;

    private final Supplier<RequestCorrelationOptions> optionsSupplier;
    private static final String OPTIONS = AbstractJaxRsRequestCorrelationProvider.class.getName() + ".OPTIONS";
    private static final String PREVIOUS_CONTEXT = AbstractJaxRsRequestCorrelationProvider.class.getName()
            + ".PREVIOUS_CONTEXT";

    /// Creates a provider using a runtime-owned options supplier.
    protected AbstractJaxRsRequestCorrelationProvider(Supplier<RequestCorrelationOptions> optionsSupplier) {
        this.optionsSupplier = java.util.Objects.requireNonNull(optionsSupplier,
                "optionsSupplier must not be null");
    }

    @Override
    public final void filter(ContainerRequestContext request) throws IOException {
        var options = optionsSupplier.get();
        if (options == null || options.isPathExcluded(request.getUriInfo().getRequestUri().getPath())) {
            return;
        }
        var id = options.acceptIncoming()
                ? RequestCorrelationId.parse(request.getHeaderString(options.headerName()), options.maximumLength())
                .orElseGet(RequestCorrelationId::generate)
                : RequestCorrelationId.generate();
        var context = RequestCorrelationContext.of(id);
        request.setProperty(OPTIONS, options);
        request.setProperty(PREVIOUS_CONTEXT, RequestCorrelationContext.current().orElse(null));
        request.setProperty(RequestCorrelationContext.ATTRIBUTE_NAME, context);
        RequestCorrelationContext.install(context);
        try {
            project(request, id);
        } catch (Error error) {
            cleanupAfterProjectionFailure(request, error);
            throw error;
        } catch (RuntimeException exception) {
            cleanupAfterProjectionFailure(request, exception);
            throw exception;
        }
    }

    @Override
    public final void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var context = (RequestCorrelationContext) request.getProperty(RequestCorrelationContext.ATTRIBUTE_NAME);
        if (context == null) {
            return;
        }
        var options = (RequestCorrelationOptions) request.getProperty(OPTIONS);
        try {
            if (options != null && options.writeResponse()) {
                response.getHeaders().putSingle(options.headerName(), context.id().value());
            }
            clearProjection(request);
        } finally {
            var previous = (RequestCorrelationContext) request.getProperty(PREVIOUS_CONTEXT);
            RequestCorrelationContext.install(previous);
        }
    }

    /// Projects the identifier into a runtime logging context.
    protected void project(ContainerRequestContext request, RequestCorrelationId id) {
    }

    /// Restores and clears the runtime logging context.
    protected void clearProjection(ContainerRequestContext request) {
    }

    private void cleanupAfterProjectionFailure(ContainerRequestContext request, Throwable failure) {
        try {
            clearProjection(request);
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        } finally {
            RequestCorrelationContext.install((RequestCorrelationContext) request.getProperty(PREVIOUS_CONTEXT));
        }
    }
}
