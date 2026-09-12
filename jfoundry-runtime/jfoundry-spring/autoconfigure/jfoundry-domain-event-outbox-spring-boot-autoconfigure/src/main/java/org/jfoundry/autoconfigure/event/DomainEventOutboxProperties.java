package org.jfoundry.autoconfigure.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// Configuration for the optional Domain Event to Outbox dispatcher.
@ConfigurationProperties(prefix = "jfoundry.domain.event.dispatch.outbox")
public class DomainEventOutboxProperties {

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
