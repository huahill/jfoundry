package org.jfoundry.quarkus.persistence.jpa.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.persistence.jpa.quarkus.QuarkusJpaPersistenceFailureTranslator;
import org.jfoundry.infrastructure.persistence.jpa.quarkus.QuarkusPersistenceFailureTranslatorBinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceJpaProcessorTest {

    @Test
    void registersThePersistenceFailureTranslatorAndBinder() {
        PersistenceJpaProcessor processor = new PersistenceJpaProcessor();

        AdditionalBeanBuildItem beans = processor.registerPersistenceFailureTranslation();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusJpaPersistenceFailureTranslator.class.getName(),
                QuarkusPersistenceFailureTranslatorBinder.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
