package org.jfoundry.integration.nativeimage.jobrunr;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NativeJobRunrIT {

    private static final Path APPLICATION = Path.of(
            "target/jfoundry-spring-jobrunr-native").toAbsolutePath();

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Test
    void nativeImageSchedulesAndExecutesTheJobRunrOutboxDispatcher() throws Exception {
        assertThat(Files.isExecutable(APPLICATION))
                .as("JobRunr Native Image executable")
                .isTrue();

        int port = availablePort();
        Process application = new ProcessBuilder(
                APPLICATION.toString(),
                "--server.port=" + port,
                "--spring.datasource.url=" + postgresql.getJdbcUrl(),
                "--spring.datasource.username=" + postgresql.getUsername(),
                "--spring.datasource.password=" + postgresql.getPassword(),
                "--jfoundry.outbox.dispatcher.mode=jobrunr",
                "--jfoundry.outbox.dispatcher.cron=*/1 * * * * *",
                "--jobrunr.background-job-server.enabled=true",
                "--jobrunr.background-job-server.poll-interval-in-seconds=1",
                "--jobrunr.miscellaneous.allow-anonymous-data-usage=false")
                .redirectErrorStream(true)
                .redirectOutput(Path.of("target/native-jobrunr.log").toFile())
                .start();

        try {
            assertThat(awaitDispatchResult(port)).contains(
                    "\"dispatched\":true",
                    "\"published\":true");
        } finally {
            stop(application);
        }
    }

    private static String awaitDispatchResult(int port) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        IOException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                var connection = (java.net.HttpURLConnection) new java.net.URI(
                        "http://127.0.0.1:" + port + "/jfoundry/native/jobrunr/dispatch")
                        .toURL()
                        .openConnection();
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(1_000);
                if (connection.getResponseCode() == 200) {
                    String result = new String(connection.getInputStream().readAllBytes());
                    if (result.contains("\"dispatched\":true")) {
                        return result;
                    }
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(500);
        }
        throw new IOException("Native JobRunr application did not dispatch its Outbox message", lastFailure);
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
