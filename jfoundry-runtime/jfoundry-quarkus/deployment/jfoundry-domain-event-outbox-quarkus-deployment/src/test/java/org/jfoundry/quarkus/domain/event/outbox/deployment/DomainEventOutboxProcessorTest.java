package org.jfoundry.quarkus.domain.event.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.OutboxDomainEventDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.QuarkusOutboxExternalizationProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventOutboxProcessorTest {

    @Test
    void registersTheDomainEventOutboxRuntimeBeans() {
        AdditionalBeanBuildItem beans = new DomainEventOutboxProcessor().registerDomainEventOutboxRuntime();

        assertThat(beans.getBeanClasses()).contains(
                OutboxDomainEventDispatcher.class.getName(),
                QuarkusOutboxExternalizationProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
