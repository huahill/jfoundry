package org.jfoundry.autoconfigure.webmvc;

import java.util.List;

import org.jfoundry.http.HttpLoggingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Spring Boot properties for inbound JFoundry Web MVC integrations.
@ConfigurationProperties(prefix = "jfoundry.web.mvc")
public class JfoundryWebMvcProperties {

    private HttpLoggingLevel loggingLevel = HttpLoggingLevel.NONE;

    private List<String> loggingExcludedPaths = List.of("/actuator/health/**");

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
}
