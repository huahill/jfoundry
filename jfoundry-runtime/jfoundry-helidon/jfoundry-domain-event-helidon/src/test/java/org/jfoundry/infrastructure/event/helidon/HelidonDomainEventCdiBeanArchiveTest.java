package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.event.cdi.CdiDomainEventDispatcher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonDomainEventCdiBeanArchiveTest {

    @Test
    void publishesCdiDiscoveryResources() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/beans.xml"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/jandex.idx"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/services/jakarta.enterprise.inject.spi.Extension"));
    }

    @Test
    void declaresConstructorInjectionAndDependentScope() {
        assertInjectionConstructor(HelidonDomainEventContext.class);
        assertTrue(HelidonDomainEventContext.class.isAnnotationPresent(Dependent.class));
        assertTrue(HelidonDomainEventScope.class.isAnnotationPresent(Dependent.class));
        assertInjectionConstructor(HelidonAggregateEventRegistrarBinder.class);
        assertTrue(HelidonAggregateEventRegistrarBinder.class.isAnnotationPresent(Dependent.class));
        assertInjectionConstructor(CdiDomainEventDispatcherProducer.class);
        assertTrue(CdiDomainEventDispatcherProducer.class.isAnnotationPresent(Dependent.class));
        assertInjectionConstructor(DomainEventDispatchCoordinatorProducer.class);
        assertTrue(DomainEventDispatchCoordinatorProducer.class.isAnnotationPresent(Dependent.class));
        assertInjectionConstructor(CdiDomainEventDispatcher.class);
        assertFalse(Modifier.isFinal(CdiDomainEventDispatcher.class.getModifiers()));
    }

    private static void assertInjectionConstructor(Class<?> beanType) {
        boolean injectionConstructorPresent = java.util.Arrays.stream(beanType.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));

        assertTrue(injectionConstructorPresent, () -> beanType.getName() + " must declare a CDI injection constructor");
    }
}
