package org.jfoundry.http.helidon;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jfoundry.http.correlation.RequestCorrelationId;
import org.jfoundry.http.correlation.RequestCorrelationOptions;
import org.jfoundry.http.jaxrs.AbstractJaxRsRequestCorrelationProvider;
import org.jfoundry.http.jaxrs.RequestCorrelationPathMatcher;

/// Registers inbound request correlation with Helidon MP.
@Provider
@PreMatching
@Priority(Priorities.USER - 300)
public final class RequestCorrelationProvider extends AbstractJaxRsRequestCorrelationProvider {

    public static final int PRIORITY = Priorities.USER - 300;
    public static final String CONFIG_PREFIX = "jfoundry.web.helidon.request-correlation";

    /// Creates a provider backed by MicroProfile configuration.
    public RequestCorrelationProvider() {
        super(RequestCorrelationProvider::configuredOptions);
    }

    private static RequestCorrelationOptions configuredOptions() {
        var config = ConfigProvider.getConfig();
        if (!config.getOptionalValue(CONFIG_PREFIX + ".enabled", Boolean.class).orElse(true)) {
            return null;
        }
        var headerName = config.getOptionalValue(CONFIG_PREFIX + ".header-name", String.class)
                .orElse("X-Request-Id");
        var acceptIncoming = config.getOptionalValue(CONFIG_PREFIX + ".accept-incoming", Boolean.class)
                .orElse(true);
        var writeResponse = config.getOptionalValue(CONFIG_PREFIX + ".write-response", Boolean.class)
                .orElse(true);
        var maximumLength = config.getOptionalValue(CONFIG_PREFIX + ".maximum-length", Integer.class)
                .orElse(RequestCorrelationId.DEFAULT_MAXIMUM_LENGTH);
        var excluded = config.getOptionalValue(CONFIG_PREFIX + ".excluded-paths", String.class)
                .stream().flatMap(value -> java.util.Arrays.stream(value.split(","))).map(String::trim).toList();
        return new RequestCorrelationOptions(headerName, acceptIncoming, writeResponse, maximumLength,
                RequestCorrelationPathMatcher.predicate(excluded));
    }
}
