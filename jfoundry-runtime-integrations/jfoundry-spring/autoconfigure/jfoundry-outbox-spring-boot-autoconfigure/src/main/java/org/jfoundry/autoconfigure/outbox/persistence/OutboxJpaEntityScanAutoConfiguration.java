package org.jfoundry.autoconfigure.outbox.persistence;

import org.jfoundry.autoconfigure.jpa.JpaEntityScanPackages;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageEntity;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/// Registers the Outbox entity before Spring Boot creates the JPA EntityManagerFactory.
@AutoConfiguration
@AutoConfigureBefore(name = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass({JpaOutboxMessageEntity.class, JpaOutboxMessageStore.class})
@Import(OutboxJpaEntityScanAutoConfiguration.EntityScanRegistrar.class)
public class OutboxJpaEntityScanAutoConfiguration {

    static final class EntityScanRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            JpaEntityScanPackages.register(registry, JpaOutboxMessageEntity.class);
        }
    }
}
