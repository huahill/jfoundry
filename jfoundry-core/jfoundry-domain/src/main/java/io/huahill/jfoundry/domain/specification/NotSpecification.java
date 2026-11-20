package io.huahill.jfoundry.domain.specification;

import java.util.Objects;

/// Negation specification.
/// <p>
/// Negates another specification.
///
/// @param specification specification to negate
/// @param <T> candidate type
public record NotSpecification<T>(Specification<T> specification) implements Specification<T> {

    public NotSpecification {
        Objects.requireNonNull(specification, "specification must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return !specification.isSatisfiedBy(candidate);
    }
}
