package org.jfoundry.autoconfigure.webmvc;

import org.jfoundry.http.HttpLoggingLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Spring Boot properties for inbound JFoundry Web MVC integrations.
@ConfigurationProperties(prefix = "jfoundry.web.mvc")
public class JfoundryWebMvcProperties {

    private HttpLoggingLevel loggingLevel = HttpLoggingLevel.NONE;

    /// Returns the detail recorded for inbound Servlet HTTP logs.
    public HttpLoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    /// Sets the detail recorded for inbound Servlet HTTP logs.
    public void setLoggingLevel(HttpLoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}
