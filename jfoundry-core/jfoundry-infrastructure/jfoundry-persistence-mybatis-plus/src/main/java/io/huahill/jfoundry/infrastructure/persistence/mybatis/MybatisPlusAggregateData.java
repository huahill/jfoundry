package io.huahill.jfoundry.infrastructure.persistence.mybatis;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Base class for a MyBatis-Plus aggregate root row.
///
/// This type is not a persistence-neutral or JPA contract. MyBatis-Plus writes the row object
/// produced by {@link MybatisPlusDataMapper}; it does not track a managed entity instance.
/// The identifier is the database primary-key type, such as {@link String}, {@link Long}, or
/// {@link java.util.UUID}. Domain aggregates keep their own identifiers, and
/// {@link MybatisPlusDataMapper} converts them at the repository boundary.
///
/// @param <ID> persistence identifier type, which must be serializable
public abstract class MybatisPlusAggregateData<ID extends Serializable> {

    private @Nullable ID id;

    public @Nullable ID getId() {
        return id;
    }

    public void setId(@Nullable ID id) {
        this.id = id;
    }

    /// {@code hashCode} and {@code equals} use the identifier.
    ///
    /// A new row whose identifier is still null uses identity hash and never equals another
    /// instance. This keeps distinct unsaved rows from collapsing in a {@code HashSet} or
    /// {@code HashMap}.
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
        MybatisPlusAggregateData<?> that = (MybatisPlusAggregateData<?>) obj;
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
