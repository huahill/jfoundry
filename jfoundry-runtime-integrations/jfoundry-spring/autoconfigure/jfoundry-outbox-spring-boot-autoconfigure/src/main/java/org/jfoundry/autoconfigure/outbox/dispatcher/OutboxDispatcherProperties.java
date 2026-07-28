package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// Outbox dispatch trigger configuration.
///
/// Binds the `jfoundry.outbox.dispatcher.*` prefix. This class lives in
/// jfoundry-spring-boot-autoconfigure because it is a Spring Boot property binding model, not a
/// Spring runtime adapter contract.
@ConfigurationProperties(prefix = "jfoundry.outbox.dispatcher")
public class OutboxDispatcherProperties {

    /// Dispatch trigger implementation. Defaults to `SCHEDULED`.
    private Mode mode = Mode.SCHEDULED;

    /// Delay in milliseconds between scheduled-mode dispatch attempts. Defaults to 5000.
    private long intervalMs = 5000;

    /// Cron expression used by JobRunr mode. Defaults to every ten seconds.
    private String cron = "*/10 * * * * *";

    /// Maximum number of pending messages claimed by one dispatch attempt. Defaults to 50.
    private int batchSize = 50;

    /// Maximum delivery attempts before a message is dead-lettered. Defaults to 5.
    private int maxRetries = 5;

    /// Initial retry backoff in milliseconds. Defaults to 1000.
    private long backoffBaseMs = 1000;

    /// Maximum retry backoff in milliseconds. Defaults to 300000.
    private long backoffMaxMs = 300_000;

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public long getIntervalMs() { return intervalMs; }
    public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public long getBackoffBaseMs() { return backoffBaseMs; }
    public void setBackoffBaseMs(long backoffBaseMs) { this.backoffBaseMs = backoffBaseMs; }
    public long getBackoffMaxMs() { return backoffMaxMs; }
    public void setBackoffMaxMs(long backoffMaxMs) { this.backoffMaxMs = backoffMaxMs; }

    public enum Mode {
        SCHEDULED,
        JOBRUNR,
        NONE
    }
}
