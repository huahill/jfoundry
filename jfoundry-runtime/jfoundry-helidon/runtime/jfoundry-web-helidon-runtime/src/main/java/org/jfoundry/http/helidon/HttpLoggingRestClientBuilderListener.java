package org.jfoundry.http.helidon;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientBuilderListener;

/// Registers JFoundry HTTP logging with every MicroProfile REST Client builder.
public final class HttpLoggingRestClientBuilderListener implements RestClientBuilderListener {

    @Override
    public void onNewBuilder(RestClientBuilder builder) {
        builder.register(HttpLoggingProvider.class);
    }
}
