package io.huahill.jfoundry.domain.specification;

import java.util.Objects;

/// Disjunction specification.
/// <p>
/// Returns true when at least one specification is satisfied.
///
/// @param left left specification
/// @param right right specification
/// @param <T> candidate type
public record OrSpecification<T>(
        Specification<T> left,
        Specification<T> right
) implements Specification<T> {

    public OrSpecification {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
    }

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return left.isSatisfiedBy(candidate) || right.isSatisfiedBy(candidate);
    }
}
