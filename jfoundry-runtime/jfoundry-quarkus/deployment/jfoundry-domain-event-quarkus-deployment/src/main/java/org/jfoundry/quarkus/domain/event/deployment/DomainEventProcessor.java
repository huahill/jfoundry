package org.jfoundry.quarkus.domain.event.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;
import org.jfoundry.application.ApplicationService;
import org.jfoundry.infrastructure.event.quarkus.CdiDomainEventDispatcher;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventContext;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventDispatch;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventDispatchInterceptor;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventScope;

import java.util.List;

/// Registers JFoundry local domain-event adapters during Quarkus augmentation.
class DomainEventProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerDomainEventScopeAndContext() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(CdiDomainEventDispatcher.class)
                .addBeanClass(QuarkusDomainEventScope.class)
                .addBeanClass(QuarkusDomainEventContext.class)
                .setUnremovable()
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerDomainEventDispatchInterceptor() {
        return AdditionalBeanBuildItem.unremovableOf(QuarkusDomainEventDispatchInterceptor.class);
    }

    @BuildStep
    List<AnnotationsTransformerBuildItem> bindApplicationServicesToDomainEventDispatch(
            CombinedIndexBuildItem combinedIndex) {
        DotName applicationService = DotName.createSimple(ApplicationService.class.getName());
        return combinedIndex.getIndex().getAnnotations(applicationService).stream()
                .filter(annotation -> annotation.target().kind() == org.jboss.jandex.AnnotationTarget.Kind.CLASS)
                .map(annotation -> annotation.target().asClass().name())
                .map(className -> new AnnotationsTransformerBuildItem(
                        AnnotationTransformation.forClasses()
                                .whenClass(className)
                                .transform(transformation -> transformation.add(QuarkusDomainEventDispatch.class))))
                .toList();
    }
}
