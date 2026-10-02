package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonRedissonLockCdiBeanArchiveTest {

    @Test
    void publishesCdiDiscoveryResources() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/beans.xml"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/jandex.idx"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/services/jakarta.enterprise.inject.spi.Extension"));
    }

    @Test
    void declaresConstructorInjectionForTheInterceptor() {
        boolean injectionConstructorPresent = Arrays.stream(HelidonDistributedLockInterceptor.class.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));
        assertTrue(injectionConstructorPresent);
        assertTrue(HelidonRedissonLockProducer.class.isAnnotationPresent(ApplicationScoped.class));
    }
}
