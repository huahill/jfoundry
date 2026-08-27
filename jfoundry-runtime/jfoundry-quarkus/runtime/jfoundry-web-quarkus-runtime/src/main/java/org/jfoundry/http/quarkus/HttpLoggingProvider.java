package org.jfoundry.http.quarkus;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.jfoundry.http.jaxrs.AbstractJaxRsServerHttpLoggingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/// Registers shared JAX-RS HTTP logging with Quarkus REST.
@Provider
@PreMatching
@Priority(Priorities.USER - 200)
public final class HttpLoggingProvider extends AbstractJaxRsServerHttpLoggingProvider {

    /// Configuration key for inbound Quarkus REST logging.
    public static final String SERVER_LOGGING_LEVEL = "jfoundry.web.quarkus.logging-level";

    private static final Logger LOG = LoggerFactory.getLogger(HttpLoggingProvider.class);

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public HttpLoggingProvider() {
        this(LOG::isInfoEnabled, System::nanoTime);
    }

    HttpLoggingProvider(BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        super(SERVER_LOGGING_LEVEL, infoEnabled, nanoTime,
                (message, arguments) -> LOG.info(MessageFormat.format(message, arguments)));
    }
}
