package org.jfoundry.quarkus.transaction.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.transaction.quarkus.QuarkusTransactionRunner;

/// Registers the JFoundry transaction adapter during Quarkus augmentation.
class TransactionProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerTransactionRunner() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusTransactionRunner.class)
                .setUnremovable()
                .setDefaultScope(DotName.createSimple(ApplicationScoped.class.getName()))
                .build();
    }
}
