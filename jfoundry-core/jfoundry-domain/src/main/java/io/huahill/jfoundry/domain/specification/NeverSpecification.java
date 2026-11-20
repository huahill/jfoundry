package io.huahill.jfoundry.domain.specification;

/// Specification that is never satisfied.
///
/// @param <T> candidate type
public final class NeverSpecification<T> implements Specification<T> {

    @Override
    public boolean isSatisfiedBy(T candidate) {
        return false;
    }
}
