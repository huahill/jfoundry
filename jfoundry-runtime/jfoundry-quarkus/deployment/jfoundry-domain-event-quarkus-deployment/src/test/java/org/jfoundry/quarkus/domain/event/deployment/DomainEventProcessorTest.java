package org.jfoundry.quarkus.domain.event.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.jandex.DotName;
import org.jfoundry.infrastructure.event.quarkus.DelegatingCdiDomainEventDispatcher;
import org.jfoundry.infrastructure.event.quarkus.DelegatingDomainEventDispatchCoordinator;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventContext;
import org.jfoundry.infrastructure.event.quarkus.QuarkusDomainEventScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventProcessorTest {

    @Test
    void registersCdiDomainEventDispatcherAsUnremovableBean() {
        AdditionalBeanBuildItem beans = new DomainEventProcessor().registerCdiDomainEventDispatcher();

        assertThat(beans.getBeanClasses()).containsExactly(DelegatingCdiDomainEventDispatcher.class.getName());
        assertThat(beans.isRemovable()).isFalse();
        assertThat(beans.getDefaultScope()).isEqualTo(DotName.createSimple(ApplicationScoped.class.getName()));
    }

    @Test
    void registersDomainEventBeansAsUnremovableApplicationScopedBeans() {
        AdditionalBeanBuildItem beans = new DomainEventProcessor().registerDomainEventScopeAndContext();

        assertThat(beans.getBeanClasses()).containsExactly(
                QuarkusDomainEventScope.class.getName(),
                QuarkusDomainEventContext.class.getName());
        assertThat(beans.isRemovable()).isFalse();
        assertThat(beans.getDefaultScope()).isEqualTo(DotName.createSimple(ApplicationScoped.class.getName()));
    }

    @Test
    void registersDomainEventDispatchCoordinatorAsUnremovableBean() {
        AdditionalBeanBuildItem beans = new DomainEventProcessor().registerDomainEventDispatchCoordinator();

        assertThat(beans.getBeanClasses()).containsExactly(DelegatingDomainEventDispatchCoordinator.class.getName());
        assertThat(beans.isRemovable()).isFalse();
        assertThat(beans.getDefaultScope()).isEqualTo(DotName.createSimple(ApplicationScoped.class.getName()));
    }
}
