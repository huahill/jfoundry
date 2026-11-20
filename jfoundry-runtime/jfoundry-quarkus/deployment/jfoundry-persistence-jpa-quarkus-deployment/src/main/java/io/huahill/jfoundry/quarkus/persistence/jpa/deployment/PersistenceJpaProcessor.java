package io.huahill.jfoundry.quarkus.persistence.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.huahill.jfoundry.infrastructure.persistence.jpa.quarkus.QuarkusJpaPersistenceFailureTranslator;
import io.huahill.jfoundry.infrastructure.persistence.jpa.quarkus.QuarkusPersistenceFailureTranslatorBinder;

/// Registers Quarkus JPA persistence failure translation during augmentation.
class PersistenceJpaProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerPersistenceFailureTranslation() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusJpaPersistenceFailureTranslator.class)
                .addBeanClass(QuarkusPersistenceFailureTranslatorBinder.class)
                .setUnremovable()
                .build();
    }
}
