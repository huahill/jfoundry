package io.huahill.jfoundry.domain.entity;

import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Entity;
import org.jmolecules.ddd.types.Identifier;

/// Base class for entities that live inside an aggregate.
///
/// Provides common entity behavior:
/// - identifier management.
/// - no event publication capability; aggregate roots record domain events.
/// - no parent reference, avoiding implicit event bubbling from entities to aggregate roots.
///
/// @param <T>  aggregate root type that owns this entity
/// @param <ID> entity identifier type, independent from the aggregate root identifier type
///
public abstract class BaseEntity<T extends AggregateRoot<T, ?>, ID extends Identifier>
        implements Entity<T, ID> {

    /// Entity identifier.
    private ID id;

    public BaseEntity(ID id) {
        this.id = id;
    }

    @Override
    public ID getId() {
        return id;
    }

    /// Reassigns the entity identifier.
    /// <p>
    /// This method is intended for subclasses and persistence restoration. Business code should
    /// prefer assigning the identifier through the constructor.
    ///
    /// @param id entity identifier
    protected void identify(ID id) {
        this.id = id;
    }
}
