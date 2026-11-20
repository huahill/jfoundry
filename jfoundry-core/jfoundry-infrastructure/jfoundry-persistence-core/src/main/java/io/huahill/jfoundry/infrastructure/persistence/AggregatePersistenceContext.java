package io.huahill.jfoundry.infrastructure.persistence;

/// Tracks persistence-owned state for one aggregate object during a persistence scope.
/// <p>
/// Implementations must use aggregate object identity rather than aggregate equality or
/// identifier equality. Runtime integrations define the scope lifecycle, typically one
/// application transaction.
public interface AggregatePersistenceContext {

    /// Attaches state and rejects an existing value for the same aggregate instance and key.
    <S> void attach(Object aggregate, PersistenceStateKey<S> key, S state);

    /// Returns attached state or fails when the aggregate is not tracked for the key.
    <S> S require(Object aggregate, PersistenceStateKey<S> key);

    /// Replaces existing state or fails when the aggregate is not tracked for the key.
    <S> void replace(Object aggregate, PersistenceStateKey<S> key, S state);
}
