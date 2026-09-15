package org.jfoundry.quarkus.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxDispatchProducer;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxMaintenance;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxTemplateProducer;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxTrigger;

/// Registers the Outbox dispatcher with Quarkus during augmentation.
class OutboxProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerOutboxRuntime() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusOutboxDispatchProducer.class)
                .addBeanClass(QuarkusOutboxTrigger.class)
                .addBeanClass(QuarkusOutboxMaintenance.class)
                .addBeanClass(QuarkusOutboxTemplateProducer.class)
                .setUnremovable()
                .build();
    }
}
