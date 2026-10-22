package org.jfoundry.autoconfigure.web;

import org.jfoundry.http.spring.client.HttpOperationRequestProcessor;
import org.jfoundry.web.spring.client.RestClientSupport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/// Configures JFoundry's outbound `RestClient` support for Spring Boot-managed builders.
@AutoConfiguration(afterName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class, RestClientSupport.class})
@EnableConfigurationProperties(JfoundryWebProperties.class)
public class WebRestClientAutoConfiguration {

    /// Applies the configured JFoundry response handling and HTTP logging detail to each builder.
    @Bean
    @ConditionalOnMissingBean(name = "jfoundryWebRestClientCustomizer")
    public RestClientCustomizer jfoundryWebRestClientCustomizer(JfoundryWebProperties properties) {
        return builder -> RestClientSupport.configure(builder, properties.getRestClient().getLogging().getLevel(),
                properties.getRestClient().getLogging().getFormat(),
                properties.getRestClient().getLogging().getIncludedHeaders());
    }

    /// Names each HTTP interface call for {@link org.jfoundry.http.spring.client.HttpLoggingInterceptor}.
    @Bean
    @ConditionalOnClass(HttpServiceProxyFactory.class)
    public RestClientHttpServiceGroupConfigurer jfoundryHttpOperationLoggingConfigurer() {
        return groups -> groups.forEachProxyFactory((group, factory) -> HttpOperationRequestProcessor.apply(factory));
    }
}
