package org.jfoundry.autoconfigure.inbox;

import org.jfoundry.autoconfigure.jpa.JpaEntityScanPackages;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageEntity;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/// Registers the Inbox entity before Spring Boot creates the JPA EntityManagerFactory.
@AutoConfiguration
@AutoConfigureBefore(name = "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
@ConditionalOnClass({JpaInboxMessageEntity.class, JpaInboxMessageStore.class})
@Import(InboxJpaEntityScanAutoConfiguration.EntityScanRegistrar.class)
public class InboxJpaEntityScanAutoConfiguration {

    static final class EntityScanRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                            BeanDefinitionRegistry registry) {
            JpaEntityScanPackages.register(registry, JpaInboxMessageEntity.class);
        }
    }
}
