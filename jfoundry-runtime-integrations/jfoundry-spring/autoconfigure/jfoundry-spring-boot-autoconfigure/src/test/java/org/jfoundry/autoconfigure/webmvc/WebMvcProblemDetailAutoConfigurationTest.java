package org.jfoundry.autoconfigure.webmvc;

import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.jfoundry.webmvc.spring.ProblemDetailExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcProblemDetailAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcProblemDetailAutoConfiguration.class));

    @Test
    void createsProblemDetailExceptionHandler() {
        runner.run(context -> assertThat(context).hasSingleBean(ProblemDetailExceptionHandler.class));
    }

    @Test
    void backsOffWhenUserProvidesExceptionHandler() {
        ProblemDetailExceptionHandler userHandler = new ProblemDetailExceptionHandler();

        runner.withBean(ProblemDetailExceptionHandler.class, () -> userHandler)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProblemDetailExceptionHandler.class);
                    assertThat(context.getBean(ProblemDetailExceptionHandler.class)).isSameAs(userHandler);
                });
    }

    @Test
    void composesApplicationProblemMappersIntoTheHandler() {
        ProblemMapper mapper = exception -> java.util.Optional.of(new ProblemDescriptor(
                java.net.URI.create("https://example.test/problems/application"), "Application failure", 422,
                "Application detail", java.util.Map.of("code", "APPLICATION_FAILURE")));

        runner.withBean(ProblemMapper.class, () -> mapper)
                .run(context -> assertThat(context.getBean(ProblemDetailExceptionHandler.class)
                        .handleInvalidArgument(new InvalidArgumentException("internal"))
                        .getBody().getProperties()).containsEntry("code", "APPLICATION_FAILURE"));
    }
}
