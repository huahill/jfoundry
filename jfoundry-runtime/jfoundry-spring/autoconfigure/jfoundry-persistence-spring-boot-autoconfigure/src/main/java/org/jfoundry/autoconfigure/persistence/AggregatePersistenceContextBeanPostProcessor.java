package org.jfoundry.autoconfigure.persistence;

import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.AggregatePersistenceContextAware;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;

final class AggregatePersistenceContextBeanPostProcessor implements BeanPostProcessor {

    private final BeanFactory beanFactory;

    AggregatePersistenceContextBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory must not be null.");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AggregatePersistenceContextAware aware) {
            AggregatePersistenceContext context = beanFactory
                    .getBeanProvider(AggregatePersistenceContext.class)
                    .getIfAvailable();
            if (context != null) {
                aware.setAggregatePersistenceContext(context);
            }
        }
        return bean;
    }
}
