package org.jfoundry.quarkus.integration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusRedissonLockIT {

    @Test
    void nativeImageAcquiresAndReleasesARedissonLock() throws Exception {
        Path application = Path.of(System.getProperty("native.image.path")).toAbsolutePath();
        assertThat(Files.isExecutable(application))
                .as("Redisson Native Image executable")
                .isTrue();

        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");
                GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                        .withExposedPorts(6379)) {
            database.start();
            redis.start();
            int port = availablePort();
            Process nativeImage = new ProcessBuilder(
                    application.toString(),
                    "-Dquarkus.http.port=" + port,
                    "-Dquarkus.datasource.jdbc.url=" + database.getJdbcUrl(),
                    "-Dquarkus.datasource.username=" + database.getUsername(),
                    "-Dquarkus.datasource.password=" + database.getPassword(),
                    "-Dquarkus.hibernate-orm.schema-management.strategy=drop-and-create",
                    "-Dquarkus.redisson.single-server-config.address=redis://"
                            + redis.getHost() + ":" + redis.getMappedPort(6379))
                    .redirectErrorStream(true)
                    .redirectOutput(Path.of("target/native-redisson-quarkus.log").toFile())
                    .start();
            try {
                assertThat(awaitLockResult(port)).contains("\"locked\":true");
            } finally {
                stop(nativeImage);
            }
        }
    }

    private static String awaitLockResult(int port) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        IOException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                var connection = (java.net.HttpURLConnection) new java.net.URI(
                        "http://127.0.0.1:" + port + "/jfoundry/native/redisson/lock")
                        .toURL()
                        .openConnection();
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(1_000);
                if (connection.getResponseCode() == 200) {
                    return new String(connection.getInputStream().readAllBytes());
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(250);
        }
        throw new IOException("Native Redisson application did not acquire its lock", lastFailure);
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
