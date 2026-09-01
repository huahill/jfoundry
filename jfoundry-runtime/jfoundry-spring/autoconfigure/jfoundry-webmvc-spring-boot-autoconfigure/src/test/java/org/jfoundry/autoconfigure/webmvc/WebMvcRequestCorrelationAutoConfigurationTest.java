package org.jfoundry.autoconfigure.webmvc;

import jakarta.servlet.DispatcherType;
import org.jfoundry.http.correlation.RequestCorrelationId;
import org.jfoundry.web.spring.filter.RequestCorrelationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcRequestCorrelationAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcRequestCorrelationAutoConfiguration.class));

    @Test
    void enablesCorrelationByDefaultBeforeHttpLogging() {
        runner.run(context -> {
            var registration = registration(context);
            assertThat(registration.isEnabled()).isTrue();
            assertThat(registration.getOrder()).isEqualTo(WebMvcRequestCorrelationAutoConfiguration.DEFAULT_FILTER_ORDER);
            assertThat(registration.isAsyncSupported()).isTrue();
            assertThat(registration.determineDispatcherTypes()).isEqualTo(
                    EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
            assertThat(registration.getFilter()).isInstanceOf(RequestCorrelationFilter.class);
        });
    }

    @Test
    void disabledCorrelationDoesNotRegisterFilter() {
        runner.withPropertyValues("jfoundry.web.mvc.request-correlation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean("jfoundryRequestCorrelationFilterRegistration"));
    }

    @Test
    void bindsIncomingAndResponseOptions() {
        runner.withPropertyValues(
                "jfoundry.web.mvc.request-correlation.accept-incoming=false",
                "jfoundry.web.mvc.request-correlation.write-response=false",
                "jfoundry.web.mvc.request-correlation.maximum-length=48")
                .run(context -> {
            var properties = context.getBean(JfoundryWebMvcProperties.class);
                    assertThat(properties.getRequestCorrelation().isAcceptIncoming()).isFalse();
                    assertThat(properties.getRequestCorrelation().isWriteResponse()).isFalse();
                    assertThat(properties.getRequestCorrelation().getMaximumLength()).isEqualTo(48);
                    assertThat(RequestCorrelationId.MINIMUM_GENERATED_LENGTH).isEqualTo(36);
                });
    }

    @SuppressWarnings("unchecked")
    private static FilterRegistrationBean<RequestCorrelationFilter> registration(
            org.springframework.context.ApplicationContext context) {
        return (FilterRegistrationBean<RequestCorrelationFilter>) context.getBean(
                "jfoundryRequestCorrelationFilterRegistration", FilterRegistrationBean.class);
    }
}
