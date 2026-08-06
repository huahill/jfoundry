package org.jfoundry.quarkus.inbox.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageEntity;
import org.jfoundry.infrastructure.inbox.quarkus.QuarkusJpaInboxProducer;

import java.util.Set;

/// Registers JPA Inbox components with Quarkus during augmentation.
class InboxJpaProcessor {

    @BuildStep
    AdditionalJpaModelBuildItem registerInboxJpaModel() {
        return new AdditionalJpaModelBuildItem(
                JpaInboxMessageEntity.class.getName(),
                Set.of(PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME));
    }

    @BuildStep
    AdditionalBeanBuildItem registerInboxProducer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusJpaInboxProducer.class)
                .setUnremovable()
                .build();
    }
}
