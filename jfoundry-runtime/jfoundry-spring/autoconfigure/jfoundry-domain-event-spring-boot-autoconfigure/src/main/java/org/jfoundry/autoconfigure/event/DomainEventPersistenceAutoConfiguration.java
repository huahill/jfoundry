package org.jfoundry.autoconfigure.event;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

@AutoConfiguration
@AutoConfigureBefore(DomainEventDispatchAutoConfiguration.class)
@ConditionalOnClass(name = {
        "org.jfoundry.application.event.DomainEventContext",
        "org.jfoundry.infrastructure.persistence.AbstractAggregateRepository"
})
public class DomainEventPersistenceAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "domainEventContextBeanPostProcessorRegistrar")
    public static BeanFactoryPostProcessor domainEventContextBeanPostProcessorRegistrar() {
        return beanFactory -> beanFactory.addBeanPostProcessor(
                new DomainEventContextBeanPostProcessor(beanFactory));
    }
}
