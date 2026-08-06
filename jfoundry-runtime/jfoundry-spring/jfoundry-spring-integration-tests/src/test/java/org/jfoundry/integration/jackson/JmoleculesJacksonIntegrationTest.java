package org.jfoundry.integration.jackson;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies that jMolecules Jackson 3 upstream auto-configuration registers its module with ObjectMapper.
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
        assertThat(objectMapper.registeredModules())
                .as("jMolecules Jackson upstream auto-configuration must register JMoleculesModule")
                .anyMatch(id -> id.toString().contains("jmolecules"));
    }
}
