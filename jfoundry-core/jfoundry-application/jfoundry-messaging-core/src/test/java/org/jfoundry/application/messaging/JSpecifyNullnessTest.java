package org.jfoundry.application.messaging;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void messagingPackageAndSendFailureContractAreAnnotated() throws NoSuchMethodException {
        assertTrue(SendResult.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(SendResult.class.getMethod("errorMessage")
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
