package org.jfoundry.autoconfigure.transaction;

import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.infrastructure.transaction.spring.SpringTransactionRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Auto-configuration for application-layer transaction boundaries.
@AutoConfiguration(afterName = "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration")
@ConditionalOnClass({TransactionRunner.class, TransactionTemplate.class})
public class TransactionRunnerAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.jfoundry.infrastructure.transaction.spring.SpringTransactionRunner")
    static class SpringTransactionRunnerConfiguration {

        @Bean
        @ConditionalOnBean(PlatformTransactionManager.class)
        @ConditionalOnMissingBean(TransactionRunner.class)
        public SpringTransactionRunner transactionRunner(PlatformTransactionManager transactionManager) {
            return new SpringTransactionRunner(transactionManager);
        }
    }
}
