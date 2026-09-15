package org.jfoundry.quarkus.domain.event.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.OutboxDomainEventDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.QuarkusOutboxExternalizationProducer;
import org.jmolecules.event.annotation.Externalized;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;

import java.util.List;

/// Registers the explicit Domain Event to Outbox composition during Quarkus augmentation.
class DomainEventOutboxProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerDomainEventOutboxRuntime() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(OutboxDomainEventDispatcher.class)
                .addBeanClass(QuarkusOutboxExternalizationProducer.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    List<ReflectiveHierarchyBuildItem> registerExternalizedEventsForJackson(CombinedIndexBuildItem combinedIndex) {
        DotName externalized = DotName.createSimple(Externalized.class.getName());
        return combinedIndex.getIndex().getAnnotations(externalized).stream()
                .filter(annotation -> annotation.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(annotation -> annotation.target().asClass().name())
                .map(eventType -> ReflectiveHierarchyBuildItem.builder(eventType)
                        .index(combinedIndex.getIndex())
                        .methods(true)
                        .fields(true)
                        .build())
                .toList();
    }
}
