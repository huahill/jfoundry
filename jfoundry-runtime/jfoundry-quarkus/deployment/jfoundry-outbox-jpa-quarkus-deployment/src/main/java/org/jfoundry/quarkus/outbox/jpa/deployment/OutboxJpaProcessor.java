package org.jfoundry.quarkus.outbox.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageEntity;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusJpaOutboxMessageStoreProducer;

import java.util.Set;

/// Registers the JPA Outbox store with Quarkus during augmentation.
class OutboxJpaProcessor {

    @BuildStep
    AdditionalJpaModelBuildItem registerOutboxJpaModel() {
        return new AdditionalJpaModelBuildItem(
                JpaOutboxMessageEntity.class.getName(),
                Set.of(PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME));
    }

    @BuildStep
    AdditionalBeanBuildItem registerOutboxMessageStoreProducer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusJpaOutboxMessageStoreProducer.class)
                .setUnremovable()
                .build();
    }
}
