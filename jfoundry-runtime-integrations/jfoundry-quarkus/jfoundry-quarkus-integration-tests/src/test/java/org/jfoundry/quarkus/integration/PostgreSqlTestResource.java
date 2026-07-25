package org.jfoundry.quarkus.integration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;

/// Starts PostgreSQL for Quarkus middleware verification and supplies the datasource configuration.
public final class PostgreSqlTestResource implements QuarkusTestResourceLifecycleManager {

    private final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");

    @Override
    public Map<String, String> start() {
        database.start();
        return Map.of(
                "quarkus.datasource.jdbc.url", database.getJdbcUrl(),
                "quarkus.datasource.username", database.getUsername(),
                "quarkus.datasource.password", database.getPassword(),
                "quarkus.hibernate-orm.schema-management.strategy", "drop-and-create");
    }

    @Override
    public void stop() {
        database.stop();
    }
}
