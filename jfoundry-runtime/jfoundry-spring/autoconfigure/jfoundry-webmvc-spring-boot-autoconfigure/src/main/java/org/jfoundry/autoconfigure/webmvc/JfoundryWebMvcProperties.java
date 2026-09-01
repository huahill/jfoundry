package org.jfoundry.autoconfigure.webmvc;

import java.util.List;

import org.jfoundry.http.HttpLoggingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Spring Boot properties for inbound JFoundry Web MVC integrations.
@ConfigurationProperties(prefix = "jfoundry.web.mvc")
public class JfoundryWebMvcProperties {

    private HttpLoggingLevel loggingLevel = HttpLoggingLevel.NONE;

    private List<String> loggingExcludedPaths = List.of("/actuator/health/**");

    private RequestCorrelationProperties requestCorrelation = new RequestCorrelationProperties();

    /// Returns the detail recorded for inbound Servlet HTTP logs.
    public HttpLoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /// Sets the detail recorded for inbound Servlet HTTP logs.
    public void setLoggingLevel(HttpLoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }

    /// Returns application paths excluded from inbound Servlet HTTP logs.
    public List<String> getLoggingExcludedPaths() {
        return loggingExcludedPaths;
    }

    /// Sets application paths excluded from inbound Servlet HTTP logs.
    public void setLoggingExcludedPaths(List<String> loggingExcludedPaths) {
        this.loggingExcludedPaths = loggingExcludedPaths;
    }

    /// Returns inbound request-correlation properties.
    public RequestCorrelationProperties getRequestCorrelation() {
        return requestCorrelation;
    }

    /// Sets inbound request-correlation properties.
    public void setRequestCorrelation(RequestCorrelationProperties requestCorrelation) {
        this.requestCorrelation = requestCorrelation;
    }

    public static class RequestCorrelationProperties {

        private boolean enabled = true;

        private String headerName = "X-Request-Id";

        private boolean acceptIncoming = true;

        private boolean writeResponse = true;

        private int maximumLength = 64;

        private List<String> excludedPaths = List.of();

        /// Returns whether request correlation is enabled.
        public boolean isEnabled() {
            return enabled;
        }

        /// Sets whether request correlation is enabled.
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /// Returns the inbound correlation header name.
        public String getHeaderName() {
            return headerName;
        }

        /// Sets the inbound correlation header name.
        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        /// Returns whether valid incoming values are accepted.
        public boolean isAcceptIncoming() {
            return acceptIncoming;
        }

        /// Sets whether valid incoming values are accepted.
        public void setAcceptIncoming(boolean acceptIncoming) {
            this.acceptIncoming = acceptIncoming;
        }

        /// Returns whether the final value is written to the response.
        public boolean isWriteResponse() {
            return writeResponse;
        }

        /// Sets whether the final value is written to the response.
        public void setWriteResponse(boolean writeResponse) {
            this.writeResponse = writeResponse;
        }

        /// Returns the maximum accepted correlation value length.
        public int getMaximumLength() {
            return maximumLength;
        }

        /// Sets the maximum accepted correlation value length.
        public void setMaximumLength(int maximumLength) {
            this.maximumLength = maximumLength;
        }

        /// Returns Ant-style application paths excluded from correlation.
        public List<String> getExcludedPaths() {
            return excludedPaths;
        }

        /// Sets Ant-style application paths excluded from correlation.
        public void setExcludedPaths(List<String> excludedPaths) {
            this.excludedPaths = excludedPaths;
        }
    }
}
