package org.jfoundry.http.helidon;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.jfoundry.http.jaxrs.AbstractJaxRsRestClientLoggingProvider;

/// Provides outbound MicroProfile REST Client logging for Helidon MP.
public final class RestClientHttpLoggingProvider extends AbstractJaxRsRestClientLoggingProvider {

    private static final System.Logger LOG = System.getLogger(RestClientHttpLoggingProvider.class.getName());

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public RestClientHttpLoggingProvider() {
        this(() -> LOG.isLoggable(System.Logger.Level.INFO), System::nanoTime);
    }

    RestClientHttpLoggingProvider(BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        super(infoEnabled, nanoTime,
                (message, arguments) -> LOG.log(System.Logger.Level.INFO, message, arguments));
    }
}
