package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.ApplicationService;
import org.jfoundry.application.event.CompositeDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher;
import org.jfoundry.infrastructure.outbox.spring.externalization.OutboxDomainEventDispatcher;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;

import java.util.List;
import java.util.function.Supplier;

@AutoConfiguration
@AutoConfigureAfter(name = "org.jfoundry.autoconfigure.event.DomainEventOutboxRecorderAutoConfiguration")
@EnableConfigurationProperties(DomainEventDispatchProperties.class)
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
    @ConditionalOnProperty(prefix = "jfoundry.domain.event.dispatch", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    static class DispatchConfiguration {

        @Bean
        @Primary
        @ConditionalOnBean(DomainEventDispatcher.class)
        @ConditionalOnMissingBean(CompositeDomainEventDispatcher.class)
        public CompositeDomainEventDispatcher compositeDomainEventDispatcher(
                List<DomainEventDispatcher> dispatchers) {
            return new CompositeDomainEventDispatcher(dispatchers);
        }

        @Bean
        @ConditionalOnBean(CompositeDomainEventDispatcher.class)
        @ConditionalOnMissingBean
        public DomainEventDispatchInterceptor domainEventDispatchInterceptor(
                DomainEventScope scope,
                DomainEventDispatcher dispatcher) {
            return new DomainEventDispatchInterceptor(scope, dispatcher);
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.jfoundry.infrastructure.event.spring.dispatcher.SpringApplicationEventDispatcher")
    @ConditionalOnProperty(prefix = "jfoundry.domain.event.dispatch", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    static class SpringDispatchConfiguration {

        @Bean
        @ConditionalOnMissingBean(SpringApplicationEventDispatcher.class)
        @ConditionalOnProperty(prefix = "jfoundry.domain.event.dispatch.spring", name = "enabled",
                               havingValue = "true", matchIfMissing = true)
        public SpringApplicationEventDispatcher springApplicationEventDispatcher(
                ApplicationEventPublisher eventPublisher) {
            return new SpringApplicationEventDispatcher(eventPublisher);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.jfoundry.infrastructure.outbox.spring.externalization.OutboxDomainEventDispatcher")
    @ConditionalOnProperty(prefix = "jfoundry.domain.event.dispatch", name = "enabled",
                           havingValue = "true", matchIfMissing = true)
    static class OutboxDispatchConfiguration {

        @Bean
        @ConditionalOnBean(DomainEventOutboxRecorder.class)
        @ConditionalOnMissingBean(OutboxDomainEventDispatcher.class)
        @ConditionalOnProperty(prefix = "jfoundry.domain.event.dispatch.outbox", name = "enabled",
                               havingValue = "true")
        public OutboxDomainEventDispatcher outboxDomainEventDispatcher(
                ObjectProvider<DomainEventOutboxRecorder> outboxRecorder) {
            Supplier<DomainEventOutboxRecorder> outboxRecorderSupplier = outboxRecorder::getObject;
            return new OutboxDomainEventDispatcher(outboxRecorderSupplier);
        }
    }
}
