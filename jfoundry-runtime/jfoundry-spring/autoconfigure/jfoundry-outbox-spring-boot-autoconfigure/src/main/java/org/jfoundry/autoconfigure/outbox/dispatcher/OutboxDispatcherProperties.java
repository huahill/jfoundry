package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/// Dispatch-algorithm settings for {@code jfoundry.outbox.dispatcher.*}.
///
/// Worker schedule keys ({@code enabled}, {@code interval}, {@code batch-size}, recovery, and
/// cleanup) are read by {@code JdkOutboxWorkerSettings} from the Environment, not this class.
@ConfigurationProperties(prefix = "jfoundry.outbox.dispatcher")
public class OutboxDispatcherProperties {

    /// Maximum delivery attempts before a message is dead-lettered. Defaults to 5.
    private int maxRetries = 5;

    /// Initial retry backoff. Defaults to 1s.
    private Duration backoffBase = Duration.ofSeconds(1);

    /// Maximum retry backoff. Defaults to 5m.
    private Duration backoffMax = Duration.ofMinutes(5);

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public Duration getBackoffMax() {
        return backoffMax;
    }

    public void setBackoffMax(Duration backoffMax) {
        this.backoffMax = backoffMax;
    }
}
