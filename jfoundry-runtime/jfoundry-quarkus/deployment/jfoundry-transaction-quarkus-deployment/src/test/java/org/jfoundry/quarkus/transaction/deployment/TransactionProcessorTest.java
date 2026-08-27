package org.jfoundry.quarkus.transaction.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.transaction.quarkus.QuarkusTransactionRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionProcessorTest {

    @Test
    void registersTransactionRunnerAsAnUnremovableApplicationScopedBean() {
        AdditionalBeanBuildItem beans = new TransactionProcessor().registerTransactionRunner();

        assertThat(beans.getBeanClasses()).contains(QuarkusTransactionRunner.class.getName());
        assertThat(beans.isRemovable()).isFalse();
        assertThat(beans.getDefaultScope()).isEqualTo(DotName.createSimple(ApplicationScoped.class.getName()));
    }
}
