package org.jfoundry.starter.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingStarterJsonTest {

    @Test
    void configuresJacksonPayloadSerializationWithoutAWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ObjectMapper.class);
                    assertThat(ClassUtils.isPresent(
                            "org.springframework.web.servlet.DispatcherServlet",
                            getClass().getClassLoader())).isFalse();
                });
    }
}
