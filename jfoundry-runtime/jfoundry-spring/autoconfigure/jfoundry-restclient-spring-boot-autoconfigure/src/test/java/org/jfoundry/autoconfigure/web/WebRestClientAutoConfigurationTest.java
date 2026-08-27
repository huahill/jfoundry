package org.jfoundry.autoconfigure.web;

import org.jfoundry.http.HttpLoggingLevel;
import org.jfoundry.http.spring.client.HttpLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WebRestClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RestClientAutoConfiguration.class,
                    WebRestClientAutoConfiguration.class));

    @Test
    void disablesLoggingByDefault() {
        contextRunner.run(context -> {
            assertThat(context.getBean(JfoundryWebProperties.class).getRestClient().getLoggingLevel())
                    .isEqualTo(HttpLoggingLevel.NONE);
            RestClient.Builder builder = RestClient.builder();
            context.getBean(RestClientCustomizer.class).customize(builder);
            AtomicReference<List<ClientHttpRequestInterceptor>> interceptors = new AtomicReference<>();
            builder.requestInterceptors(value -> interceptors.set(List.copyOf(value)));
            assertThat(interceptors).hasValue(List.of());
        });
    }

    @Test
    void bindsConfiguredLoggingLevelAndAppliesItToRestClientBuilder() {
        contextRunner
                .withPropertyValues("jfoundry.web.rest-client.logging-level=FULL")
                .run(context -> {
                    assertThat(context.getBean(JfoundryWebProperties.class).getRestClient().getLoggingLevel())
                            .isEqualTo(HttpLoggingLevel.FULL);
                    assertThat(configuredInterceptor(context)).extracting(interceptor ->
                            ReflectionTestUtils.getField(interceptor, "level"))
                            .isEqualTo(HttpLoggingLevel.FULL);
                });
    }

    @Test
    void appliesConfiguredLoggingLevelToBootManagedBuilder() {
        contextRunner
                .withPropertyValues("jfoundry.web.rest-client.logging-level=HEADERS")
                .run(context -> assertThat(configuredInterceptor(context.getBean(RestClient.Builder.class)))
                        .extracting(interceptor -> ReflectionTestUtils.getField(interceptor, "level"))
                        .isEqualTo(HttpLoggingLevel.HEADERS));
    }

    @Test
    void disablesTheInterceptorWhenConfiguredAsNone() {
        contextRunner
                .withPropertyValues("jfoundry.web.rest-client.logging-level=NONE")
                .run(context -> {
                    RestClient.Builder builder = RestClient.builder();
                    context.getBean(RestClientCustomizer.class).customize(builder);
                    AtomicReference<List<ClientHttpRequestInterceptor>> interceptors = new AtomicReference<>();
                    builder.requestInterceptors(value -> interceptors.set(List.copyOf(value)));

                    assertThat(interceptors).hasValue(List.of());
                });
    }

    @Test
    void keepsUserRestClientCustomizersAlongsideJfoundryCustomizer() {
        RestClientCustomizer userCustomizer = builder -> {
        };

        contextRunner
                .withBean(RestClientCustomizer.class, () -> userCustomizer)
                .run(context -> assertThat(context.getBeansOfType(RestClientCustomizer.class).values())
                        .contains(userCustomizer)
                        .hasSize(2));
    }

    private static HttpLoggingInterceptor configuredInterceptor(
            org.springframework.context.ApplicationContext context) {
        return configuredInterceptor(context.getBean(RestClientCustomizer.class));
    }

    private static HttpLoggingInterceptor configuredInterceptor(RestClientCustomizer customizer) {
        RestClient.Builder builder = RestClient.builder();
        customizer.customize(builder);
        return configuredInterceptor(builder);
    }

    private static HttpLoggingInterceptor configuredInterceptor(RestClient.Builder builder) {
        AtomicReference<List<ClientHttpRequestInterceptor>> interceptors = new AtomicReference<>();
        builder.requestInterceptors(value -> interceptors.set(List.copyOf(value)));
        assertThat(interceptors.get()).singleElement().isInstanceOf(HttpLoggingInterceptor.class);
        return (HttpLoggingInterceptor) interceptors.get().getFirst();
    }
}
