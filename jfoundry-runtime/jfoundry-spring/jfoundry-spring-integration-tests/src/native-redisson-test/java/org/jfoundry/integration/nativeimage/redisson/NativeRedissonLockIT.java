package org.jfoundry.integration.nativeimage.redisson;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NativeRedissonLockIT {

    private static final Path APPLICATION = Path.of(
            "target/jfoundry-spring-redisson-native").toAbsolutePath();

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Test
    void nativeImageAcquiresAndReleasesARedissonLock() throws Exception {
        assertThat(Files.isExecutable(APPLICATION))
                .as("Redisson Native Image executable")
                .isTrue();

        int port = availablePort();
        Process application = new ProcessBuilder(
                APPLICATION.toString(),
                "--server.port=" + port,
                "--spring.data.redis.host=" + redis.getHost(),
                "--spring.data.redis.port=" + redis.getMappedPort(6379))
                .redirectErrorStream(true)
                .redirectOutput(Path.of("target/native-redisson.log").toFile())
                .start();

        try {
            assertThat(awaitLockResult(port)).contains("\"locked\":true");
        } finally {
            stop(application);
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
