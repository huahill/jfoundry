package org.jfoundry.quarkus.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusJobRunrOutboxIT {

    @Test
    void nativeImageSchedulesAndExecutesTheJobRunrOutboxTrigger() throws Exception {
        Path application = Path.of(System.getProperty("native.image.path")).toAbsolutePath();
        assertThat(Files.isExecutable(application))
                .as("JobRunr Native Image executable")
                .isTrue();

        try (PostgreSQLContainer database = new PostgreSQLContainer("postgres:17-alpine")) {
            database.start();
            int port = availablePort();
            Process nativeImage = new ProcessBuilder(
                    application.toString(),
                    "-Dquarkus.http.port=" + port,
                    "-Dquarkus.datasource.jdbc.url=" + database.getJdbcUrl(),
                    "-Dquarkus.datasource.username=" + database.getUsername(),
                    "-Dquarkus.datasource.password=" + database.getPassword(),
                    "-Dquarkus.hibernate-orm.schema-management.strategy=drop-and-create",
                    "-Djfoundry.outbox.dispatcher.mode=jobrunr",
                    "-Djfoundry.outbox.dispatcher.enabled=true",
                    "-Djfoundry.outbox.dispatcher.interval=1s",
                    "-Djfoundry.outbox.dispatcher.cron=*/5 * * * * *",
                    "-Dquarkus.jobrunr.background-job-server.enabled=true",
                    "-Dquarkus.jobrunr.background-job-server.poll-interval-in-seconds=5",
                    "-Dquarkus.jobrunr.background-job-server.thread-type=PlatformThreads",
                    "-Dquarkus.jobrunr.background-job-server.worker-count=2",
                    "-Dquarkus.jobrunr.dashboard.enabled=false",
                    "-Dquarkus.jobrunr.database.type=sql",
                    "-Dquarkus.jobrunr.miscellaneous.allow-anonymous-data-usage=false")
                    .redirectErrorStream(true)
                    .redirectOutput(Path.of("target/native-jobrunr-quarkus.log").toFile())
                    .start();
            try {
                assertThat(awaitDispatchResult(port)).contains(
                        "\"registered\":true",
                        "\"dispatched\":true",
                        "\"published\":true");
            } finally {
                stop(nativeImage);
            }
        }
    }

    private static String awaitDispatchResult(int port) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
        IOException lastFailure = null;
        String latest = "";
        while (Instant.now().isBefore(deadline)) {
            try {
                var connection = (HttpURLConnection) URI.create(
                        "http://127.0.0.1:" + port + "/jfoundry/native/jobrunr/dispatch").toURL().openConnection();
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(1_000);
                if (connection.getResponseCode() == 200) {
                    latest = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    if (latest.contains("\"dispatched\":true") && latest.contains("\"published\":true")) {
                        return latest;
                    }
                } else if (connection.getErrorStream() != null) {
                    latest = new String(connection.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(500);
        }
        throw new IOException("Native JobRunr application did not dispatch its Outbox message: " + latest, lastFailure);
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void stop(Process application) throws InterruptedException {
        application.destroy();
        if (!application.waitFor(10, TimeUnit.SECONDS)) {
            application.destroyForcibly();
            application.waitFor();
        }
    }
}
