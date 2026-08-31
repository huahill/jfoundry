package org.jfoundry.autoconfigure.webmvc;

import java.util.EnumSet;

import jakarta.servlet.DispatcherType;
import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.web.spring.filter.HttpLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WebMvcHttpLoggingAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebMvcHttpLoggingAutoConfiguration.class));

    @Test
    void defaultsToDisabledNoneRegistration() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JfoundryWebMvcProperties.class);
            assertThat(context.getBean(JfoundryWebMvcProperties.class).getLoggingLevel())
                    .isEqualTo(HttpLoggingLevel.NONE);
            assertThat(context.getBean(JfoundryWebMvcProperties.class).getLoggingExcludedPaths())
                    .containsExactly("/actuator/health/**");
            var registration = registration(context);
            assertThat(registration.isEnabled()).isFalse();
            assertThat(level(registration)).isEqualTo(HttpLoggingLevel.NONE);
        });
    }

    @Test
    void excludesDefaultHealthPathsAfterContextPath() throws Exception {
        runner.withPropertyValues("jfoundry.web.mvc.logging-level=BASIC")
                .run(context -> {
                    var request = new MockHttpServletRequest("GET", "/api/actuator/health/liveness");
                    request.setContextPath("/api");
                    var response = new MockHttpServletResponse();
                    var filter = registration(context).getFilter();
                    filter.doFilter(request, response, (actualRequest, actualResponse) -> {
                        assertThat(actualRequest).isSameAs(request);
                        assertThat(actualResponse).isSameAs(response);
                    });
                    assertThat(request.getAttribute(
                            "org.jfoundry.web.spring.filter.HttpLoggingFilter.STATE")).isNull();
                });
    }

    @Test
    void applicationExcludedPathsReplaceDefaultsAndCanAddCustomPatterns() throws Exception {
        runner.withPropertyValues(
                "jfoundry.web.mvc.logging-level=BASIC",
                "jfoundry.web.mvc.logging-excluded-paths[0]=/internal/**",
                "jfoundry.web.mvc.logging-excluded-paths[1]=/actuator/health/**")
                .run(context -> {
                    assertThat(context.getBean(JfoundryWebMvcProperties.class).getLoggingExcludedPaths())
                            .containsExactly("/internal/**", "/actuator/health/**");
                    var request = new MockHttpServletRequest("GET", "/api/internal/metrics");
                    request.setContextPath("/api");
                    var response = new MockHttpServletResponse();
                    registration(context).getFilter().doFilter(request, response,
                            (actualRequest, actualResponse) -> {
                            });
                    assertThat(request.getAttribute(
                            "org.jfoundry.web.spring.filter.HttpLoggingFilter.STATE")).isNull();
                });
    }

    @Test
    void applicationExcludedPathsReplaceTheDefaultHealthPattern() throws Exception {
        runner.withPropertyValues(
                "jfoundry.web.mvc.logging-level=BASIC",
                "jfoundry.web.mvc.logging-excluded-paths[0]=/internal/**")
                .run(context -> {
                    var request = new MockHttpServletRequest("GET", "/api/actuator/health/liveness");
                    request.setContextPath("/api");
                    var response = new MockHttpServletResponse();
                    registration(context).getFilter().doFilter(request, response,
                            (actualRequest, actualResponse) -> {
                            });
                    assertThat(request.getAttribute(
                            "org.jfoundry.web.spring.filter.HttpLoggingFilter.STATE")).isNotNull();
                });
    }

    @Test
    void bindsEveryEnabledLevel() {
        for (var level : new HttpLoggingLevel[]{HttpLoggingLevel.BASIC, HttpLoggingLevel.HEADERS,
                HttpLoggingLevel.FULL}) {
            runner.withPropertyValues("jfoundry.web.mvc.logging-level=" + level)
                    .run(context -> {
                        var registration = registration(context);
                        assertThat(registration.isEnabled()).isTrue();
                        assertThat(level(registration)).isEqualTo(level);
                    });
        }
    }

    @Test
    void configuresAsyncDispatchTypesAndDefaultOrder() {
        runner.withPropertyValues("jfoundry.web.mvc.logging-level=BASIC")
                .run(context -> {
                    var registration = registration(context);
                    assertThat(registration.isAsyncSupported()).isTrue();
                    assertThat(registration.getOrder())
                            .isEqualTo(WebMvcHttpLoggingAutoConfiguration.DEFAULT_FILTER_ORDER);
                    assertThat(registration.determineDispatcherTypes()).isEqualTo(
                            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
                });
    }

    @Test
    void doesNotConfigureInANonServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebMvcHttpLoggingAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JfoundryWebMvcProperties.class);
                    assertThat(context).doesNotHaveBean("jfoundryHttpLoggingFilterRegistration");
                });
    }

    @Test
    void doesNotConfigureWhenTheFilterClassIsMissing() {
        runner.withClassLoader(new FilteredClassLoader(HttpLoggingFilter.class))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JfoundryWebMvcProperties.class);
                    assertThat(context).doesNotHaveBean("jfoundryHttpLoggingFilterRegistration");
                });
    }

    @Test
    void backsOffForAUserProvidedFilter() {
        var userFilter = new HttpLoggingFilter(HttpLoggingLevel.HEADERS);

        runner.withBean(HttpLoggingFilter.class, () -> userFilter)
                .run(context -> {
                    assertThat(context).hasSingleBean(HttpLoggingFilter.class);
                    assertThat(context.getBean(HttpLoggingFilter.class)).isSameAs(userFilter);
                    assertThat(context).doesNotHaveBean("jfoundryHttpLoggingFilterRegistration");
                });
    }

    @Test
    void backsOffForAUserProvidedRegistration() {
        runner.withUserConfiguration(UserRegistrationConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context).doesNotHaveBean("jfoundryHttpLoggingFilterRegistration");
                    assertThat(context.getBean(FilterRegistrationBean.class).getOrder()).isEqualTo(123);
                });
    }

    @SuppressWarnings("unchecked")
    private static FilterRegistrationBean<HttpLoggingFilter> registration(
            org.springframework.context.ApplicationContext context) {
        return (FilterRegistrationBean<HttpLoggingFilter>) context.getBean(
                "jfoundryHttpLoggingFilterRegistration", FilterRegistrationBean.class);
    }

    private static HttpLoggingLevel level(FilterRegistrationBean<HttpLoggingFilter> registration) {
        return (HttpLoggingLevel) ReflectionTestUtils.getField(registration.getFilter(), "level");
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRegistrationConfiguration {

        @Bean
        FilterRegistrationBean<HttpLoggingFilter> userHttpLoggingFilterRegistration() {
            var registration = new FilterRegistrationBean<>(new HttpLoggingFilter(HttpLoggingLevel.BASIC));
            registration.setOrder(123);
            return registration;
        }
    }
}
