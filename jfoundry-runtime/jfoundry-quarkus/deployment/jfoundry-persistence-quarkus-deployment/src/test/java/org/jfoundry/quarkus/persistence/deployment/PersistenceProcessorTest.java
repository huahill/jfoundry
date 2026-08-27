package org.jfoundry.quarkus.persistence.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAggregatePersistenceContextBinder;
import org.jfoundry.infrastructure.persistence.quarkus.QuarkusAuditStampingProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceProcessorTest {

    @Test
    void registersPersistenceBeansAsUnremovableApplicationScopedBeans() {
        AdditionalBeanBuildItem beans = new PersistenceProcessor().registerAggregatePersistenceContext();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusAggregatePersistenceContext.class.getName(),
                QuarkusAggregatePersistenceContextBinder.class.getName(),
                QuarkusAuditStampingProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
        assertThat(beans.getDefaultScope()).isEqualTo(DotName.createSimple(ApplicationScoped.class.getName()));
    }
}
