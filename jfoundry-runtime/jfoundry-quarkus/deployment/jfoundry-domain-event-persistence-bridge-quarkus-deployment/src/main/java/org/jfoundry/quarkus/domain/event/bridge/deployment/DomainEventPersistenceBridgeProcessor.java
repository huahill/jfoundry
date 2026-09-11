package org.jfoundry.quarkus.domain.event.bridge.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventPersistenceBridgeBinder;

/// Registers the explicit Domain Event persistence bridge during Quarkus augmentation.
class DomainEventPersistenceBridgeProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerPersistenceBridge() {
        return AdditionalBeanBuildItem.unremovableOf(QuarkusDomainEventPersistenceBridgeBinder.class);
    }
}
