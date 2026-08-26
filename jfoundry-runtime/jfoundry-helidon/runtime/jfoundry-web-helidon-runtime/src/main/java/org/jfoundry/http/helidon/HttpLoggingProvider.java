package org.jfoundry.http.helidon;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jfoundry.http.jaxrs.AbstractJaxRsHttpLoggingProvider;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/// Registers shared JAX-RS HTTP logging with Helidon MP.
@Provider
@PreMatching
@Priority(Priorities.USER - 200)
public final class HttpLoggingProvider extends AbstractJaxRsHttpLoggingProvider {

    /// Configuration key for inbound Helidon MP REST logging.
    public static final String SERVER_LOGGING_LEVEL = "jfoundry.web.helidon.logging-level";

    /// Configuration key for outbound MicroProfile REST Client logging.
    public static final String CLIENT_LOGGING_LEVEL = AbstractJaxRsHttpLoggingProvider.CLIENT_LOGGING_LEVEL;

    private static final System.Logger LOG = System.getLogger(HttpLoggingProvider.class.getName());

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public HttpLoggingProvider() {
        this(() -> LOG.isLoggable(System.Logger.Level.DEBUG), System::nanoTime);
    }

    HttpLoggingProvider(BooleanSupplier debugEnabled, LongSupplier nanoTime) {
        super(SERVER_LOGGING_LEVEL, debugEnabled,
                (message, arguments) -> LOG.log(System.Logger.Level.DEBUG, message, arguments), nanoTime);
    }
}
