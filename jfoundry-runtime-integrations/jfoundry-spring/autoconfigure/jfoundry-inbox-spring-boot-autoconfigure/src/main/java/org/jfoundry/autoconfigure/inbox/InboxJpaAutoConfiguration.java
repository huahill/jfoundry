package org.jfoundry.autoconfigure.inbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategies;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import javax.sql.DataSource;
import java.sql.SQLException;

/// Auto-configures the Jakarta Persistence Inbox store.
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.jfoundry.autoconfigure.inbox.InboxMybatisPlusAutoConfiguration"
})
@AutoConfigureBefore(InboxAutoConfiguration.class)
@ConditionalOnClass({EntityManager.class, SharedEntityManagerCreator.class, JpaInboxMessageStore.class})
@ConditionalOnBean(EntityManagerFactory.class)
public class InboxJpaAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean({JpaInboxClaimStrategy.class, InboxMessageStore.class})
    public JpaInboxClaimStrategy jpaInboxClaimStrategy(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return JpaInboxClaimStrategies.forProductName(
                    connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine database product for JpaInboxClaimStrategy", exception);
        }
    }

    @Bean
    @ConditionalOnBean(JpaInboxClaimStrategy.class)
    @ConditionalOnMissingBean(InboxMessageStore.class)
    public JpaInboxMessageStore jpaInboxMessageStore(
            EntityManagerFactory entityManagerFactory, JpaInboxClaimStrategy claimStrategy) {
        return new JpaInboxMessageStore(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory), claimStrategy);
    }
}
