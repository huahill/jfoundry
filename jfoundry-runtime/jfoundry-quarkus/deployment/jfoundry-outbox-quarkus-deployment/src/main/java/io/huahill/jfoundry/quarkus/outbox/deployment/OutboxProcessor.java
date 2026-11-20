package io.huahill.jfoundry.quarkus.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.huahill.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxDispatchProducer;
import io.huahill.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxTemplateProducer;

/// Registers the Outbox dispatcher with Quarkus during augmentation.
class OutboxProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerOutboxRuntime() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusOutboxDispatchProducer.class)
                .addBeanClass(QuarkusOutboxTemplateProducer.class)
                .setUnremovable()
                .build();
    }
}
