package org.jfoundry.infrastructure.transaction.helidon;

import jakarta.enterprise.context.Dependent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelidonTransactionCdiBeanArchiveTest {

    @Test
    void publishesCdiDiscoveryResources() {
        assertNotNull(getClass().getClassLoader().getResource("META-INF/beans.xml"));
        assertNotNull(getClass().getClassLoader().getResource("META-INF/jandex.idx"));
    }

    @Test
    void usesDependentScopeForTheTransactionRunner() {
        assertTrue(HelidonTransactionRunner.class.isAnnotationPresent(Dependent.class));
    }
}
