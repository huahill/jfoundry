package org.jfoundry.autoconfigure.persistence;

import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;
import org.jfoundry.infrastructure.persistence.spring.SpringDataAccessFailureTranslator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

@AutoConfiguration
@ConditionalOnClass(name = "org.jfoundry.infrastructure.persistence.AbstractPersistenceAdapter")
public class PersistenceFailureAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = {
            "org.jfoundry.infrastructure.persistence.spring.SpringDataAccessFailureTranslator",
            "org.springframework.dao.DataAccessException"
    })
    @ConditionalOnMissingBean(PersistenceFailureTranslator.class)
    public PersistenceFailureTranslator persistenceFailureTranslator() {
        return new SpringDataAccessFailureTranslator();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "persistenceFailureTranslatorBeanPostProcessor")
    public static BeanFactoryPostProcessor persistenceFailureTranslatorBeanPostProcessor() {
        return beanFactory -> beanFactory.addBeanPostProcessor(
                new PersistenceFailureTranslatorBeanPostProcessor(beanFactory));
    }
}
