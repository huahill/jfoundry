package org.jfoundry.quarkus.integration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public final class QuarkusJobRunrIdleProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return QuarkusJobRunrIntegrationSupport.jobRunrConfig(false);
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return QuarkusJobRunrIntegrationSupport.postgresResource();
    }

    @Override
    public boolean disableGlobalTestResources() {
        return true;
    }
}
