package org.jfoundry.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = BootStarterSmokeTest.TestApp.class)
class BootStarterSmokeTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Test
    void contextStartsWithBaseStarterOnly() {
    }
}
