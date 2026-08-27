package org.jfoundry.http.quarkus;

import java.text.MessageFormat;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.jfoundry.http.jaxrs.AbstractJaxRsRestClientLoggingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Provides outbound MicroProfile REST Client logging for Quarkus.
public final class RestClientHttpLoggingProvider extends AbstractJaxRsRestClientLoggingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RestClientHttpLoggingProvider.class);

    /// Creates a provider that reads the current MicroProfile configuration for each request.
    public RestClientHttpLoggingProvider() {
        this(LOG::isInfoEnabled, System::nanoTime);
    }

    RestClientHttpLoggingProvider(BooleanSupplier infoEnabled, LongSupplier nanoTime) {
        super(infoEnabled, nanoTime, (message, arguments) -> LOG.info(MessageFormat.format(message, arguments)));
    }
}
