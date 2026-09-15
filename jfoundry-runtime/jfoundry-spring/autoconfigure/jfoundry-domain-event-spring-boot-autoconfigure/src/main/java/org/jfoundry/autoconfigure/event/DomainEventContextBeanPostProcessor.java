package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.event.DomainEventContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceObserverAware;
import org.jfoundry.infrastructure.persistence.event.DomainEventAggregatePersistenceObserver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;

final class DomainEventContextBeanPostProcessor implements BeanPostProcessor {

    private final BeanFactory beanFactory;

    DomainEventContextBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory must not be null.");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AggregatePersistenceObserverAware aware) {
            DomainEventContext context = beanFactory.getBeanProvider(DomainEventContext.class).getIfAvailable();
            if (context != null) {
                aware.setAggregatePersistenceObserver(new DomainEventAggregatePersistenceObserver(context));
            }
        }
        return bean;
    }
}
