package io.huahill.jfoundry.infrastructure.persistence.jpa;

/// Maps between a pure domain aggregate and one Jakarta Persistence entity graph.
///
/// @param <A> aggregate type
/// @param <ID> aggregate identifier type
/// @param <E> JPA entity type
/// @param <K> JPA identifier type
public interface JpaAggregateMapper<A, ID, E, K> {

    K toEntityId(ID id);

    E newEntity(A aggregate);

    A toAggregate(E entity);

    /// Applies current aggregate state to the managed entity loaded in this persistence context.
    /// <p>
    /// When the entity-graph root is versioned, every graph mutation must also change a persistent
    /// root attribute. A root {@code @Version} alone does not make child-only updates participate
    /// in optimistic concurrency. A mapper may use an explicit root touch method for this purpose.
    void apply(A aggregate, E managedEntity);
}
