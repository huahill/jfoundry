package org.jfoundry.helidon.integration;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionResourceTest {

    @Test
    void declaresItsTransactionRunnerConstructorForCdiInjection() {
        boolean injectionConstructorPresent = java.util.Arrays.stream(TransactionResource.class.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class));

        assertTrue(injectionConstructorPresent);
    }
}
