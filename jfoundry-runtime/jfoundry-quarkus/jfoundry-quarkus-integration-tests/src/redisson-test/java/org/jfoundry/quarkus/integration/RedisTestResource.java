package org.jfoundry.quarkus.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

/// Starts Redis for Quarkus Redisson verification and supplies the client address.
public final class RedisTestResource implements QuarkusTestResourceLifecycleManager {

    private final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    @Override
    public Map<String, String> start() {
        redis.start();
        return Map.of(
                "quarkus.redisson.single-server-config.address",
                "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Override
    public void stop() {
        redis.stop();
    }
}
