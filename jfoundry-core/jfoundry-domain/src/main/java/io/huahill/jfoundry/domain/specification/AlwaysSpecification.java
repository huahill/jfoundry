package io.huahill.jfoundry.domain.specification;

/// Specification that is always satisfied.
///
/// @param <T> candidate type
public final class AlwaysSpecification<T> implements Specification<T> {

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return true;
    }
}
