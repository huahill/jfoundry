package org.jfoundry.autoconfigure.persistence;

import org.jfoundry.infrastructure.persistence.AbstractPersistenceAdapter;
import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.Objects;

final class PersistenceFailureTranslatorBeanPostProcessor implements BeanPostProcessor {

    private final BeanFactory beanFactory;

    PersistenceFailureTranslatorBeanPostProcessor(BeanFactory beanFactory) {
        this.beanFactory = Objects.requireNonNull(beanFactory, "BeanFactory must not be null.");
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AbstractPersistenceAdapter adapter) {
            PersistenceFailureTranslator translator = beanFactory
                    .getBeanProvider(PersistenceFailureTranslator.class)
                    .getIfAvailable();
            if (translator != null) {
                adapter.setPersistenceFailureTranslator(translator);
            }
        }
        return bean;
    }
}
