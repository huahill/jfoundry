package org.jfoundry.integration.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies that jMolecules Jackson upstream auto-configuration registers its module with ObjectMapper.
@SpringBootTest(classes = JmoleculesJacksonIntegrationTest.TestApp.class)
class JmoleculesJacksonIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void jmoleculesModuleIsRegisteredByUpstreamAutoConfiguration() {
        assertThat(objectMapper.getRegisteredModuleIds())
                .as("jMolecules Jackson upstream auto-configuration must register JMoleculesModule")
                .anyMatch(id -> id.toString().contains("jmolecules"));
    }
}
