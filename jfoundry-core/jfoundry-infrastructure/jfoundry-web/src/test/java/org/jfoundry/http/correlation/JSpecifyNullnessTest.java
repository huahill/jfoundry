package org.jfoundry.http.correlation;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void correlationPackageAndOptionalContractsAreAnnotated() throws NoSuchMethodException {
        assertTrue(RequestCorrelationId.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(RequestCorrelationId.class.getMethod("parse", String.class)
                .getParameters()[0]
                .getAnnotatedType()
                .isAnnotationPresent(Nullable.class));
        assertTrue(RequestCorrelationContext.class.getMethod("install", RequestCorrelationContext.class)
                .getParameters()[0]
                .getAnnotatedType()
                .isAnnotationPresent(Nullable.class));
    }
}
