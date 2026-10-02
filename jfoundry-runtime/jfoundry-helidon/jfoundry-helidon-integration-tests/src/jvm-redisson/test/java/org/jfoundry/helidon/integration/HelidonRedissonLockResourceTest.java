package org.jfoundry.helidon.integration;

import io.helidon.microprofile.testing.Socket;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("redisson-integration")
@HelidonTest
class HelidonRedissonLockResourceTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
        System.setProperty(
                "org.redisson.Redisson.default.singleServerConfig.address",
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Inject
    private LockExecutor lockExecutor;

    @Inject
    @Socket("@default")
    private WebTarget target;

    @Test
    void acquiresAProgrammaticRedissonLock() throws Exception {
        Boolean locked = lockExecutor.execute(
                new LockKey("helidon-redisson", "verification"),
                LockOptions.builder().leaseTime(Duration.ofSeconds(5)).build(),
                () -> true);

        assertEquals(Boolean.TRUE, locked);
    }

    @Test
    void acquiresAnAnnotatedRedissonLock() {
        try (Response response = target.path("/jfoundry/helidon/redisson/lock")
                .queryParam("orderId", "42")
                .request()
                .get()) {
            assertEquals(200, response.getStatus());
            assertEquals("{\"locked\":true}", response.readEntity(String.class));
        }
    }
}
