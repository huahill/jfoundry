package org.jfoundry.autoconfigure.web;

import org.jfoundry.web.spring.HttpLoggingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Spring Boot properties for outbound JFoundry Web integrations.
@ConfigurationProperties(prefix = "jfoundry.web")
public class JfoundryWebProperties {

    private final RestClient restClient = new RestClient();

    /// Returns outbound `RestClient` properties.
    public RestClient getRestClient() {
        return restClient;
    }

    /// Outbound `RestClient` properties.
    public static class RestClient {

        private HttpLoggingLevel loggingLevel = HttpLoggingLevel.BASIC;

        /// Returns the detail recorded for outbound `RestClient` HTTP logs.
        public HttpLoggingLevel getLoggingLevel() {
            return loggingLevel;
        }

        /// Sets the detail recorded for outbound `RestClient` HTTP logs.
        public void setLoggingLevel(HttpLoggingLevel loggingLevel) {
            this.loggingLevel = loggingLevel;
        }
    }
}
