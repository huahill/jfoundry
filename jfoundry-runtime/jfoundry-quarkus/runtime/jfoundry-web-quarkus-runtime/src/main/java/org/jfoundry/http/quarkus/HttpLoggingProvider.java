package org.jfoundry.http.quarkus;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jfoundry.http.jaxrs.AbstractJaxRsHttpLoggingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/// Registers shared JAX-RS HTTP logging with Quarkus REST.
@Provider
@PreMatching
@Priority(Priorities.USER - 200)
public final class HttpLoggingProvider extends AbstractJaxRsHttpLoggingProvider {

    /// Configuration key for inbound Quarkus REST logging.
    public static final String SERVER_LOGGING_LEVEL = "jfoundry.web.quarkus.logging-level";

    /// Configuration key for outbound MicroProfile REST Client logging.
    public static final String CLIENT_LOGGING_LEVEL = AbstractJaxRsHttpLoggingProvider.CLIENT_LOGGING_LEVEL;

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingProvider.class);

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public HttpLoggingProvider() {
        this(LOG::isDebugEnabled, System::nanoTime);
    }

    HttpLoggingProvider(BooleanSupplier debugEnabled, LongSupplier nanoTime) {
        super(SERVER_LOGGING_LEVEL, debugEnabled,
                (message, arguments) -> LOG.debug(MessageFormat.format(message, arguments)), nanoTime);
    }
}
