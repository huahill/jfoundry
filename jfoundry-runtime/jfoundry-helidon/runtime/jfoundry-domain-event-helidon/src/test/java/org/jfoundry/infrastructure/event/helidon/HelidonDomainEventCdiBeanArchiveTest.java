package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

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
    }

    private static void assertInjectionConstructor(Class<?> beanType) {
        boolean injectionConstructorPresent = java.util.Arrays.stream(beanType.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));

        assertTrue(injectionConstructorPresent, () -> beanType.getName() + " must declare a CDI injection constructor");
    }
}
