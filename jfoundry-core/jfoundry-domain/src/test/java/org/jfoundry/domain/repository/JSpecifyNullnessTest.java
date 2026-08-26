package org.jfoundry.domain.repository;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void repositoryPackageAndMissingAggregateContractAreAnnotated() throws NoSuchMethodException {
        assertTrue(AggregateRepository.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(AggregateRepository.class.getMethod("findById", org.jmolecules.ddd.types.Identifier.class)
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
