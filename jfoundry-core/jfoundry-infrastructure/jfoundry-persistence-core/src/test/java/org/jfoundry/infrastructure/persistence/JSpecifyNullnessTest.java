package org.jfoundry.infrastructure.persistence;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.jmolecules.ddd.types.Identifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void persistencePackageAndMissingAggregateContractAreAnnotated() throws NoSuchMethodException {
        assertTrue(AbstractAggregateRepository.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(AbstractAggregateRepository.class.getMethod("findById", Identifier.class)
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
        assertTrue(AbstractAggregateRepository.class.getDeclaredMethod("doFindById", Identifier.class)
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
        assertTrue(AggregateData.class.getMethod("getId")
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
