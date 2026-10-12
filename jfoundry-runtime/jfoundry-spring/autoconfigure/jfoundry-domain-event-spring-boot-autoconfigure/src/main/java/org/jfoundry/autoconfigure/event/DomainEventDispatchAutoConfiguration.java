package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DefaultDomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@AutoConfiguration
@ConditionalOnClass({
        ApplicationService.class,
        DomainEventDispatcher.class,
        Advisor.class
})
public class DomainEventDispatchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DomainEventScope domainEventScope() {
        return new DomainEventScope();
    }

    @Bean
    @ConditionalOnMissingBean(DomainEventContext.class)
    public ScopedValueDomainEventContext domainEventContext(DomainEventScope scope) {
        return new ScopedValueDomainEventContext(scope);
    }

    @Configuration(proxyBeanMethods = false)
    static class DispatchConfiguration {

        @Bean
        @ConditionalOnBean(DomainEventDispatcher.class)
        @ConditionalOnMissingBean(DomainEventDispatchCoordinator.class)
        public DomainEventDispatchCoordinator domainEventDispatchCoordinator(
                org.springframework.beans.factory.ObjectProvider<DomainEventDispatcher> dispatchers) {
            return new DefaultDomainEventDispatchCoordinator(dispatchers.orderedStream().toList());
        }

        @Bean
        @ConditionalOnBean(DomainEventDispatchCoordinator.class)
        @ConditionalOnMissingBean
        public DomainEventDispatchInterceptor domainEventDispatchInterceptor(
                DomainEventScope scope,
                DomainEventDispatchCoordinator coordinator) {
            return new DomainEventDispatchInterceptor(scope, coordinator);
        }

        @Bean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        @ConditionalOnBean(DomainEventDispatchInterceptor.class)
        @ConditionalOnMissingBean(name = "domainEventDispatchAdvisor")
        public static Advisor domainEventDispatchAdvisor() {
            DefaultBeanFactoryPointcutAdvisor advisor = new DefaultBeanFactoryPointcutAdvisor();
            advisor.setPointcut(new AnnotationMatchingPointcut(ApplicationService.class, true));
            advisor.setAdviceBeanName("domainEventDispatchInterceptor");
            return advisor;
        }
    }

}
