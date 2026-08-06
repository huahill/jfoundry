package org.jfoundry.quarkus.outbox.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.OutboxDomainEventDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.externalization.QuarkusOutboxExternalizationProducer;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxDispatcher;
import org.jfoundry.infrastructure.outbox.quarkus.QuarkusOutboxMaintenance;
import org.jmolecules.event.annotation.Externalized;

import java.util.List;

/// Registers the Outbox dispatcher with Quarkus during augmentation.
class OutboxProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerOutboxRuntime() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusOutboxDispatcher.class)
                .addBeanClass(QuarkusOutboxMaintenance.class)
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
