package org.jfoundry.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Base class for aggregate root persistence data objects.
/// <p>
/// This type represents persistence mapping objects used by the domain aggregate repository path.
/// The ID generic parameter is the persistence-layer primary-key type, typically a type natively
/// supported by databases and persistence frameworks such as {@link String}, {@link Long}, or
/// {@link java.util.UUID}. Domain aggregate roots may still use strongly typed jMolecules
/// identifiers; {@link DataMapper} performs the conversion at the repository boundary.
///
/// @param <ID> persistence identifier type, which must be serializable
public abstract class AggregateData<ID extends Serializable> {

    private @Nullable ID id;

    public @Nullable ID getId() {
        return id;
    }

    public void setId(@Nullable ID id) {
        this.id = id;
    }

    /// hashCode and equals are based on the ID.
    /// <p>
    /// When the ID is null for a new, non-persisted object, hashCode falls back to the object
    /// identity hash and equals refuses to return true. This prevents distinct new objects from
    /// collapsing into one entry in HashSet/HashMap. This follows the standard approach used by
    /// JPA/Hibernate {@code AbstractPersistable}.
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AggregateData<?> that = (AggregateData<?>) obj;
        if (id == null || that.id == null) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                '}';
    }
}
