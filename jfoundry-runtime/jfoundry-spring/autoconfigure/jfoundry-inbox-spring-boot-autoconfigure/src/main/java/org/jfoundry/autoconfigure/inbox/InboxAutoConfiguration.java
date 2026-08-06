package org.jfoundry.autoconfigure.inbox;

import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.inbox.InboxTemplate;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.autoconfigure.transaction.TransactionRunnerAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter({InboxMybatisPlusAutoConfiguration.class, TransactionRunnerAutoConfiguration.class})
@ConditionalOnClass(InboxTemplate.class)
public class InboxAutoConfiguration {

    @Bean
    @ConditionalOnBean({InboxMessageStore.class, TransactionRunner.class})
    @ConditionalOnMissingBean(InboxTemplate.class)
    public InboxTemplate inboxTemplate(InboxMessageStore store, TransactionRunner transactionRunner) {
        return new InboxTemplate(store, transactionRunner);
    }
}
