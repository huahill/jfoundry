package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/// Outbox cleanup job configuration.
///
/// Prefix: `jfoundry.outbox.cleanup`.
///
/// Accumulated `PUBLISHED` and `DEAD_LETTERED` terminal records in the Outbox table can slow down
/// claim and dispatch queries. `OutboxCleanupJob` periodically removes expired terminal records
/// according to `publishedRetentionDays` and `deadLetteredRetentionDays`.
///
/// Defaults:
///
/// - `interval`: 24 hours, once per day.
/// - `publishedRetentionDays`: 7 days for `PUBLISHED` records.
/// - `deadLetteredRetentionDays`: 30 days for `DEAD_LETTERED` records.
/// - `batchSize`: at most 1000 rows per batch.
@ConfigurationProperties(prefix = "jfoundry.outbox.cleanup")
public class OutboxCleanupProperties {

    /// Cleanup job interval. Defaults to 24 hours. Binds `jfoundry.outbox.cleanup.interval`,
    /// which is read by `@Scheduled`. YAML may use Duration values such as `24h`, `PT24H`, or
    /// `86400000`.
    private Duration interval = Duration.ofHours(24);

    /// `PUBLISHED` record retention in days. Defaults to 7. Records older than
    /// `now - publishedRetentionDays` are deleted.
    private int publishedRetentionDays = 7;

    /// `DEAD_LETTERED` record retention in days. Defaults to 30. Records older than
    /// `now - deadLetteredRetentionDays` are deleted. This retention is longer than the
    /// `PUBLISHED` retention to support investigation and manual reactivation.
    private int deadLetteredRetentionDays = 30;

    /// Maximum records deleted per batch. Defaults to 1000. Smaller batches reduce lock duration
    /// per transaction and avoid slowing the main claim and dispatch path.
    private int batchSize = 1000;

    /// Whether the cleanup job is enabled. When unset, cleanup follows dispatcher mode: enabled
    /// for `scheduled` and `jobrunr`, disabled for `none`. Set this property to `false` to disable
    /// cleanup in `scheduled` or `jobrunr` mode.
    private Boolean enabled;

    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }
    public int getPublishedRetentionDays() { return publishedRetentionDays; }
    public void setPublishedRetentionDays(int publishedRetentionDays) { this.publishedRetentionDays = publishedRetentionDays; }
    public int getDeadLetteredRetentionDays() { return deadLetteredRetentionDays; }
    public void setDeadLetteredRetentionDays(int deadLetteredRetentionDays) { this.deadLetteredRetentionDays = deadLetteredRetentionDays; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
