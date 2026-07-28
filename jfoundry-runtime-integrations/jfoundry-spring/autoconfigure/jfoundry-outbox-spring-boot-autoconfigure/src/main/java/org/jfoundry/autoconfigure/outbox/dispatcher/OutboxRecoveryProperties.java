package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/// Outbox `DISPATCHING` recovery job configuration.
///
/// Prefix: `jfoundry.outbox.recovery`.
///
/// A pod may crash or be killed while rows are in `DISPATCHING`, leaving records stuck in that
/// state. `OutboxRecoveryJob` periodically rolls them back according to `stuckTimeout`.
@ConfigurationProperties(prefix = "jfoundry.outbox.recovery")
public class OutboxRecoveryProperties {

    /// Whether the recovery job is enabled. When unset, recovery follows dispatcher mode: enabled
    /// for `scheduled` and `jobrunr`, disabled for `none`. Set this property to `false` to disable
    /// recovery in `scheduled` or `jobrunr` mode.
    private Boolean enabled;

    /// Recovery job interval. Defaults to 60 seconds. Binds
    /// `jfoundry.outbox.recovery.interval`, which is read by `@Scheduled`.
    private Duration interval = Duration.ofSeconds(60);

    /// `DISPATCHING` stuck threshold. Defaults to five minutes. Records with a claimed time
    /// earlier than `now - stuckTimeout` are rolled back to `PENDING`.
    private Duration stuckTimeout = Duration.ofMinutes(5);

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }
    public Duration getStuckTimeout() { return stuckTimeout; }
    public void setStuckTimeout(Duration stuckTimeout) { this.stuckTimeout = stuckTimeout; }
}
