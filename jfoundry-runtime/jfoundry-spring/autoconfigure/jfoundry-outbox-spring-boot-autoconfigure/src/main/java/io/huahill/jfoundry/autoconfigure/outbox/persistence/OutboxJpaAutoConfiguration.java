package io.huahill.jfoundry.autoconfigure.outbox.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/// Auto-configures the Jakarta Persistence Outbox store.
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration"
})
@AutoConfigureBefore(name = "io.huahill.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration")
@ConditionalOnClass({EntityManager.class, SharedEntityManagerCreator.class, JpaOutboxMessageStore.class})
@ConditionalOnBean(EntityManagerFactory.class)
public class OutboxJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxMessageStore.class)
    public JpaOutboxMessageStore jpaOutboxMessageStore(EntityManagerFactory entityManagerFactory) {
        return new JpaOutboxMessageStore(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
    }
}
