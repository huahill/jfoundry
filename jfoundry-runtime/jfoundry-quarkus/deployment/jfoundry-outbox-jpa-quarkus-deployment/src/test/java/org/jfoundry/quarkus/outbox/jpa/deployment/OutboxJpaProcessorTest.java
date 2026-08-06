package org.jfoundry.quarkus.outbox.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalJpaModelBuildItem;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageEntity;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusJpaOutboxMessageStoreProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

class OutboxJpaProcessorTest {

    @Test
    void registersTheOutboxJpaEntityAndStoreProducer() {
        OutboxJpaProcessor processor = new OutboxJpaProcessor();

        AdditionalJpaModelBuildItem model = processor.registerOutboxJpaModel();
        AdditionalBeanBuildItem beans = processor.registerOutboxMessageStoreProducer();

        assertThat(model.getClassName()).isEqualTo(JpaOutboxMessageEntity.class.getName());
        assertThat(model.getPersistenceUnits()).isEqualTo(Set.of(PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME));
        assertThat(beans.getBeanClasses()).contains(QuarkusJpaOutboxMessageStoreProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
