package org.jfoundry.autoconfigure.event;

import org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/// Registers the Spring runtime's default domain-event dispatcher.
///
/// Another `SpringApplicationEventDispatcher` replaces this bean. Other
/// `DomainEventDispatcher` beans, including an Outbox dispatcher, stay additive.
@AutoConfiguration(before = DomainEventDispatchAutoConfiguration.class)
@ConditionalOnClass(SpringApplicationEventDispatcher.class)
public class SpringEventDispatchAutoConfiguration {

    /// Publishes dispatched domain events through Spring's `ApplicationEventPublisher`.
    @Bean
    @ConditionalOnMissingBean(SpringApplicationEventDispatcher.class)
    public SpringApplicationEventDispatcher springApplicationEventDispatcher(
            ApplicationEventPublisher eventPublisher) {
        return new SpringApplicationEventDispatcher(eventPublisher);
    }
}
