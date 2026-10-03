package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

final class QuarkusJobRunrIntegrationSupport {

    private QuarkusJobRunrIntegrationSupport() {
    }

    static Map<String, String> jobRunrConfig(boolean backgroundServerEnabled) {
        return Map.ofEntries(
                Map.entry("quarkus.datasource.db-kind", "postgresql"),
                Map.entry("jfoundry.outbox.dispatcher.mode", "jobrunr"),
                Map.entry("jfoundry.outbox.dispatcher.enabled", "true"),
                Map.entry("jfoundry.outbox.dispatcher.interval", "1s"),
                Map.entry("jfoundry.outbox.dispatcher.cron", "*/5 * * * * *"),
                Map.entry("quarkus.jobrunr.background-job-server.enabled", Boolean.toString(backgroundServerEnabled)),
                Map.entry("quarkus.jobrunr.background-job-server.poll-interval-in-seconds", "5"),
                Map.entry("quarkus.jobrunr.background-job-server.thread-type", "PlatformThreads"),
                Map.entry("quarkus.jobrunr.background-job-server.worker-count", "2"),
                Map.entry("quarkus.jobrunr.dashboard.enabled", "false"),
                Map.entry("quarkus.jobrunr.database.type", "sql"),
                Map.entry("quarkus.jobrunr.miscellaneous.allow-anonymous-data-usage", "false"));
    }

    static List<QuarkusTestProfile.TestResourceEntry> postgresResource() {
        return List.of(new QuarkusTestProfile.TestResourceEntry(PostgreSqlTestResource.class));
    }
}
