package org.jfoundry.infrastructure.persistence.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonPersistenceCdiBeanArchiveTest {

    @Test
    void publishesCdiDiscoveryResources() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/beans.xml"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/jandex.idx"));
    }

    @Test
    void declaresConstructorInjectionAndDependentScope() {
        assertInjectionConstructor(HelidonAggregatePersistenceContext.class);
        assertInjectionConstructor(HelidonAggregatePersistenceContextBinder.class);
        assertTrue(HelidonAggregatePersistenceContext.class.isAnnotationPresent(Dependent.class));
        assertTrue(HelidonAggregatePersistenceContextBinder.class.isAnnotationPresent(Dependent.class));
    }

    private static void assertInjectionConstructor(Class<?> beanType) {
        boolean injectionConstructorPresent = java.util.Arrays.stream(beanType.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));

        assertTrue(injectionConstructorPresent, () -> beanType.getName() + " must declare a CDI injection constructor");
    }
}
