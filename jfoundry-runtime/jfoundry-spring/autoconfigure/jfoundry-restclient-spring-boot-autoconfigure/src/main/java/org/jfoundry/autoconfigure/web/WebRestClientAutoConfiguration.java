package org.jfoundry.autoconfigure.web;

import org.jfoundry.web.spring.client.RestClientSupport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/// Configures JFoundry's outbound `RestClient` support for Spring Boot-managed builders.
@AutoConfiguration(afterName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@ConditionalOnClass({RestClient.class, RestClientCustomizer.class, RestClientSupport.class})
@EnableConfigurationProperties(JfoundryWebProperties.class)
public class WebRestClientAutoConfiguration {

    /// Applies the configured JFoundry response handling and HTTP logging detail to each builder.
    @Bean
    @ConditionalOnMissingBean(name = "jfoundryWebRestClientCustomizer")
    public RestClientCustomizer jfoundryWebRestClientCustomizer(JfoundryWebProperties properties) {
        return builder -> RestClientSupport.configure(builder, properties.getRestClient().getLoggingLevel());
    }
}
