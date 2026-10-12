package org.jfoundry.autoconfigure.event.spring;

import org.jfoundry.autoconfigure.event.DomainEventDispatchAutoConfiguration;
import org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/// Publishes dispatched domain events through Spring's `ApplicationEventPublisher`.
@AutoConfiguration(before = DomainEventDispatchAutoConfiguration.class)
@ConditionalOnClass(SpringApplicationEventDispatcher.class)
public class SpringEventDispatchAutoConfiguration {

    /// Registers the Spring application-event dispatcher when the application has not supplied one.
    @Bean
    @ConditionalOnMissingBean(SpringApplicationEventDispatcher.class)
    public SpringApplicationEventDispatcher springApplicationEventDispatcher(
            ApplicationEventPublisher eventPublisher) {
        return new SpringApplicationEventDispatcher(eventPublisher);
    }
}
