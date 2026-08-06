package org.jfoundry.infrastructure.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.jfoundry.infrastructure.event.helidon.HelidonDomainEventContext;
import org.jfoundry.infrastructure.event.helidon.HelidonDomainEventScope;
import org.jfoundry.infrastructure.persistence.helidon.HelidonAggregatePersistenceContext;
import org.jfoundry.infrastructure.persistence.helidon.HelidonAggregatePersistenceContextBinder;
import org.jfoundry.infrastructure.transaction.helidon.HelidonTransactionRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonCdiBeanArchiveTest {

    @Test
    void publishesTheCdiBeanArchiveDescriptor() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/beans.xml"));
    }

    @Test
    void publishesTheJandexIndexForNativeCdiDiscovery() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/jandex.idx"));
    }

    @Test
    void declaresConstructorInjectionForCdiBeansWithDependencies() {
        assertInjectionConstructor(HelidonDomainEventContext.class);
        assertInjectionConstructor(HelidonAggregatePersistenceContext.class);
        assertInjectionConstructor(HelidonAggregatePersistenceContextBinder.class);
    }

    @Test
    void usesDependentScopeForStatelessNativeRuntimeBeans() {
        assertDependent(HelidonDomainEventContext.class);
        assertDependent(HelidonDomainEventScope.class);
        assertDependent(HelidonAggregatePersistenceContext.class);
        assertDependent(HelidonAggregatePersistenceContextBinder.class);
        assertDependent(HelidonTransactionRunner.class);
    }

    private static void assertInjectionConstructor(Class<?> beanType) {
        boolean injectionConstructorPresent = java.util.Arrays.stream(beanType.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));

        assertTrue(injectionConstructorPresent, () -> beanType.getName() + " must declare a CDI injection constructor");
    }

    private static void assertDependent(Class<?> beanType) {
        assertTrue(beanType.isAnnotationPresent(Dependent.class),
                () -> beanType.getName() + " must use CDI's proxy-free dependent scope");
    }
}
