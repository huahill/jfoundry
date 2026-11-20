package io.huahill.jfoundry.quarkus.outbox.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import io.huahill.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageEntity;
import io.huahill.jfoundry.infrastructure.outbox.quarkus.jpa.QuarkusJpaOutboxMessageStoreProducer;

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
