package org.jfoundry.quarkus.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxMaintenance;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.OutboxDomainEventDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.QuarkusOutboxExternalizationProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxProcessorTest {

    @Test
    void registersTheOutboxRuntimeBeans() {
        AdditionalBeanBuildItem beans = new OutboxProcessor().registerOutboxRuntime();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusOutboxDispatcher.class.getName(),
                QuarkusOutboxMaintenance.class.getName(),
                OutboxDomainEventDispatcher.class.getName(),
                QuarkusOutboxExternalizationProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
