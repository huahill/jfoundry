package org.jfoundry.autoconfigure.web;

import org.jfoundry.http.HttpLoggingFormat;
import org.jfoundry.http.HttpLoggingLevel;
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

        private final Logging logging = new Logging();

        /// Returns outbound HTTP logging properties.
        public Logging getLogging() {
            return logging;
        }
    }

    /// Outbound `RestClient` logging properties.
    public static class Logging {

        private HttpLoggingLevel level = HttpLoggingLevel.NONE;

        private HttpLoggingFormat format = HttpLoggingFormat.HUMAN;

        /// Returns the detail recorded for outbound `RestClient` HTTP logs.
        public HttpLoggingLevel getLevel() {
            return level;
        }

        /// Sets the detail recorded for outbound `RestClient` HTTP logs.
        public void setLevel(HttpLoggingLevel level) {
            this.level = level;
        }

        /// Returns the layout used for outbound `RestClient` HTTP logs.
        public HttpLoggingFormat getFormat() {
            return format;
        }

        /// Sets the layout used for outbound `RestClient` HTTP logs.
        public void setFormat(HttpLoggingFormat format) {
            this.format = format;
        }
    }
}
