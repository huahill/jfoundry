package io.github.xfoundries.jfoundry.parent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class SpringCloudCompatibilityTest {

    @Test
    void startsAnApplicationContextWithTheManagedSpringCloudLine() {
        try (var context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.main.banner-mode=off")
                .run()) {
            assertThat(context.isActive()).isTrue();
            assertThat(context.containsBean("compositeCompatibilityVerifier")).isTrue();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }
}
