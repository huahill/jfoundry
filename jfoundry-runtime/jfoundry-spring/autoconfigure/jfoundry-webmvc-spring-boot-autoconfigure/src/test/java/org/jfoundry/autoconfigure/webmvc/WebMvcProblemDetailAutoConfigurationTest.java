package org.jfoundry.autoconfigure.webmvc;

import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.problem.ProblemMapper;
import org.jfoundry.webmvc.spring.ProblemDetailsExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcProblemDetailAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcProblemDetailAutoConfiguration.class));

    @Test
    void createsProblemDetailsExceptionHandler() {
        runner.run(context -> assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class));
    }

    @Test
    void backsOffWhenUserProvidesExceptionHandler() {
        ProblemDetailsExceptionHandler userHandler = new ProblemDetailsExceptionHandler();

        runner.withBean(ProblemDetailsExceptionHandler.class, () -> userHandler)
                .run(context -> {
                    assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class);
                    assertThat(context.getBean(ProblemDetailsExceptionHandler.class)).isSameAs(userHandler);
                });
    }

    @Test
    void composesApplicationProblemMappersIntoTheHandler() {
        ProblemMapper mapper = exception -> java.util.Optional.of(new ProblemDescriptor(
                java.net.URI.create("https://example.test/problems/application"), "Application failure", 422,
                "Application detail", java.util.Map.of("retryable", false)));

        runner.withBean(ProblemMapper.class, () -> mapper)
                .run(context -> assertThat(context.getBean(ProblemDetailsExceptionHandler.class)
                        .handleInvalidArgument(new InvalidArgumentException("internal"))
                        .getBody().getProperties()).containsEntry("retryable", false));
    }

    @Test
    void localizesProblemDetailsThroughAnAvailableMessageSource() {
        org.springframework.context.support.StaticMessageSource messageSource =
                new org.springframework.context.support.StaticMessageSource();
        messageSource.addMessage("order.invalid-page-size", java.util.Locale.SIMPLIFIED_CHINESE,
                "分页大小无效：请求 {0}");
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.SIMPLIFIED_CHINESE);

        try {
            runner.withBean(org.springframework.context.MessageSource.class, () -> messageSource)
                    .run(context -> assertThat(context.getBean(ProblemDetailsExceptionHandler.class)
                            .handleInvalidArgument(new InvalidArgumentException("order.invalid-page-size", 500))
                            .getBody()).isNotNull()
                            .satisfies(problem -> {
                                org.springframework.http.ProblemDetail detail =
                                        (org.springframework.http.ProblemDetail) problem;
                                org.assertj.core.api.Assertions.assertThat(detail.getTitle()).isEqualTo("参数无效");
                                org.assertj.core.api.Assertions.assertThat(detail.getDetail())
                                        .isEqualTo("分页大小无效：请求 500");
                            }));
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void preventsBootFromRegisteringItsProblemDetailsExceptionHandler() {
        runner.withConfiguration(AutoConfigurations.of(WebMvcAutoConfiguration.class,
                        WebMvcProblemDetailAutoConfiguration.class))
                .withPropertyValues("spring.mvc.problemdetails.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ResponseEntityExceptionHandler.class);
                    assertThat(context).hasSingleBean(ProblemDetailsExceptionHandler.class);
                    assertThat(context).doesNotHaveBean("problemDetailsExceptionHandler");
                });
    }
}
