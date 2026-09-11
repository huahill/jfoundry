package org.jfoundry.quarkus.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxDispatchProducer;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxTrigger;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxMaintenance;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxTemplateProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxProcessorTest {

    @Test
    void registersTheOutboxRuntimeBeans() {
        AdditionalBeanBuildItem beans = new OutboxProcessor().registerOutboxRuntime();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusOutboxTrigger.class.getName(),
                QuarkusOutboxDispatchProducer.class.getName(),
                QuarkusOutboxMaintenance.class.getName(),
                QuarkusOutboxTemplateProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
