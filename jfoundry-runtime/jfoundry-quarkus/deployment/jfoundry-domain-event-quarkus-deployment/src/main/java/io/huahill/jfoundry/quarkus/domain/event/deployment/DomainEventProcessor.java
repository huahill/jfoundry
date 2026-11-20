package io.huahill.jfoundry.quarkus.domain.event.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.DotName;
import io.huahill.jfoundry.application.ApplicationService;
import io.huahill.jfoundry.infrastructure.event.quarkus.DelegatingCdiDomainEventDispatcher;
import io.huahill.jfoundry.infrastructure.event.quarkus.DelegatingDomainEventDispatchCoordinator;
import io.huahill.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventContext;
import io.huahill.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventDispatch;
import io.huahill.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventDispatchInterceptor;
import io.huahill.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventScope;

import java.util.List;

/// Registers JFoundry local domain-event adapters during Quarkus augmentation.
class DomainEventProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerCdiDomainEventDispatcher() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(DelegatingCdiDomainEventDispatcher.class)
                .setUnremovable()
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerDomainEventDispatchCoordinator() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(DelegatingDomainEventDispatchCoordinator.class)
                .setUnremovable()
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .build();
    }

    @BuildStep
    AdditionalBeanBuildItem registerDomainEventScopeAndContext() {
        return AdditionalBeanBuildItem.builder()
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
