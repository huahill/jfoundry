package org.jfoundry.autoconfigure.persistence;

import org.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.spring.SpringTransactionAggregatePersistenceContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/// Configures transaction-bound aggregate persistence state when Spring transaction support is
/// available. Applications may provide another runtime-specific implementation.
@AutoConfiguration
@ConditionalOnClass({
        AggregatePersistenceContext.class,
        SpringTransactionAggregatePersistenceContext.class,
        TransactionSynchronizationManager.class
})
public class AggregatePersistenceContextAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnMissingBean(name = "aggregatePersistenceContextBeanPostProcessor")
    static BeanFactoryPostProcessor aggregatePersistenceContextBeanPostProcessor() {
        return beanFactory -> beanFactory.addBeanPostProcessor(
                new AggregatePersistenceContextBeanPostProcessor(beanFactory));
    }

    @Bean
    @ConditionalOnMissingBean(AggregatePersistenceContext.class)
    AggregatePersistenceContext aggregatePersistenceContext() {
        return new SpringTransactionAggregatePersistenceContext();
    }
}
