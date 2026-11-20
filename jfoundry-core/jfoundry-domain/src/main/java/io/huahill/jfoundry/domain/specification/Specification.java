package io.huahill.jfoundry.domain.specification;

/// Domain specification.
/// <p>
/// Determines whether an in-memory candidate satisfies a business rule.
/// Specifications do not promise conversion to database predicates; persistence
/// adapters should use their underlying ORM or SQL expression capabilities.
///
/// @param <T> candidate type
public interface Specification<T> {

    /// Determines whether the candidate satisfies this specification.
    ///
    /// @param candidate candidate to test
    /// @return true when satisfied, false otherwise
    boolean isSatisfiedBy(T candidate);

    /// Combines this specification with another specification using logical AND.
    ///
    /// @param other other specification
    /// @return combined specification
    default Specification<T> and(Specification<T> other) {
        return new AndSpecification<>(this, other);
    }

    /// Combines this specification with another specification using logical OR.
    ///
    /// @param other other specification
    /// @return combined specification
    default Specification<T> or(Specification<T> other) {
        return new OrSpecification<>(this, other);
    }

    /// Negates this specification.
    ///
    /// @return negated specification
    default Specification<T> not() {
        return new NotSpecification<>(this);
    }
}
