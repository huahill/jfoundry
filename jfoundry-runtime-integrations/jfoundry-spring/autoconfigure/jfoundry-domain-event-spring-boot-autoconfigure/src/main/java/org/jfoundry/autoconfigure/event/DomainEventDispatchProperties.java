package org.jfoundry.autoconfigure.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jfoundry.domain.event")
public class DomainEventDispatchProperties {

    /**
     * Domain event dispatch options.
     */
    private final Dispatch dispatch = new Dispatch();

    public Dispatch getDispatch() {
        return dispatch;
    }

    public static class Dispatch {

        /**
         * Enables automatic domain event dispatch at application-service boundaries.
         */
        private boolean enabled = true;

        /**
         * Spring application event publishing options.
         */
        private final Spring spring = new Spring();

        /**
         * Transactional outbox recording options.
         */
        private final Outbox outbox = new Outbox();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Spring getSpring() {
            return spring;
        }

        public Outbox getOutbox() {
            return outbox;
        }
    }

    public static class Spring {

        /**
         * Enables Spring ApplicationEventPublisher dispatch.
         */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Outbox {

        /**
         * Enables transactional outbox recording for domain events.
         */
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
