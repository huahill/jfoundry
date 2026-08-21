package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public final class PostgreSqlIntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.datasource.db-kind", "postgresql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(PostgreSqlTestResource.class));
    }

    @Override
    public boolean disableGlobalTestResources() {
        return true;
    }
}
