package org.jfoundry.http.helidon;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jfoundry.http.jaxrs.AbstractJaxRsServerHttpLoggingProvider;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/// Registers shared JAX-RS HTTP logging with Helidon MP.
@Provider
@PreMatching
@Priority(Priorities.USER - 200)
public final class HttpLoggingProvider extends AbstractJaxRsServerHttpLoggingProvider {

    /// Configuration key for inbound Helidon MP REST logging detail.
    public static final String SERVER_LOGGING_LEVEL = "jfoundry.web.helidon.logging.level";

    /// Configuration key for inbound Helidon MP REST logging layout.
    public static final String SERVER_LOGGING_FORMAT = "jfoundry.web.helidon.logging.format";

    /// Configuration key for inbound Helidon MP REST included headers.
    public static final String SERVER_LOGGING_INCLUDED_HEADERS = "jfoundry.web.helidon.logging.included-headers";

    private static final System.Logger LOG = System.getLogger(HttpLoggingProvider.class.getName());

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public HttpLoggingProvider() {
        this(() -> LOG.isLoggable(System.Logger.Level.INFO), System::nanoTime);
    }

    HttpLoggingProvider(BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        super(SERVER_LOGGING_LEVEL, SERVER_LOGGING_FORMAT, SERVER_LOGGING_INCLUDED_HEADERS, infoEnabled, nanoTime,
                message -> LOG.log(System.Logger.Level.INFO, message));
    }
}
