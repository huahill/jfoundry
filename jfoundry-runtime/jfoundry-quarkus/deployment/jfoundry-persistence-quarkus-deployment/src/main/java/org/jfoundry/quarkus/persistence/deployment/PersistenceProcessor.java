package org.jfoundry.quarkus.persistence.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAggregatePersistenceContextBinder;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAuditStampingProducer;

/// Registers JFoundry aggregate persistence adapters during Quarkus augmentation.
class PersistenceProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerAggregatePersistenceContext() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusAggregatePersistenceContext.class)
                .addBeanClass(QuarkusAggregatePersistenceContextBinder.class)
                .addBeanClass(QuarkusAuditStampingProducer.class)
                .setUnremovable()
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .build();
    }
}
