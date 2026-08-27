package org.jfoundry.domain.repository;

import org.jspecify.annotations.Nullable;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;
import org.jmolecules.ddd.types.Repository;

import java.util.Collection;

/// Repository interface for aggregate roots.
/// <p>
/// A repository models a conceptual set of aggregate roots and persists their
/// lifecycle:
/// - findById: load one aggregate root by id
/// - add: add a new aggregate to the set, usually an SQL insert
/// - modify: update an existing aggregate in the set, usually an SQL update
/// - addAll / modifyAll: batch variants
/// - remove: remove an aggregate from the set, usually an SQL delete
/// <p>
/// Collection semantics:
/// - Business method names and call context naturally decide whether an
///   aggregate is new or existing; callers choose {@code add} or {@code modify}
///   explicitly, and the repository does not infer that decision.
/// - MyBatis has no persistence context / Unit of Work and cannot perform JPA
///   style dirty checking, so {@code modify} must be called explicitly.
/// <p>
/// Design constraints:
/// - Repositories are only for aggregate roots.
/// - One transaction should modify one aggregate root by default.
/// - Read models, statistics, pagination, and maintenance cleanup should be
///   exposed through named business boundaries, not generic repository criteria.
///
/// @param <T> aggregate root type
/// @param <ID> identifier type
public interface AggregateRepository<T extends AggregateRoot<T, ID>, ID extends Identifier>
        extends Repository<T, ID> {

    /// Finds an aggregate root by identifier.
    ///
    /// @param id identifier
    /// @return aggregate root, or null when it does not exist
    @Nullable T findById(ID id);

    /// Adds a new aggregate to the aggregate set.
    /// <p>
    /// This goes directly through the insert path. Primary-key conflicts are
    /// reported by the underlying database; the repository does not pre-check
    /// existence, keeping the path free of extra reads. Infrastructure support may register the
    /// successfully persisted aggregate for event dispatch at the application boundary.
    ///
    /// @param entity aggregate root
    void add(T entity);

    /// Modifies an existing aggregate in the aggregate set.
    /// <p>
    /// This goes directly through the update path. When zero rows are affected
    /// because the aggregate is missing or an optimistic-lock version conflicts,
    /// implementations throw {@link IllegalStateException} to avoid silent updates of missing
    /// objects. Infrastructure support may register the successfully persisted aggregate for event
    /// dispatch at the application boundary.
    ///
    /// @param entity aggregate root
    void modify(T entity);

    /// Adds multiple new aggregates to the aggregate set.
    /// <p>
    /// Batch semantics are sequential calls to {@link #add}. <b>This method does
    /// not provide a transaction boundary</b>; completed writes are not rolled
    /// back when a later item fails.
    /// <p>
    /// Transaction boundaries belong to the application layer. If atomicity is
    /// required, callers should manage transactions explicitly in application
    /// services and prefer splitting aggregate boundaries according to the DDD
    /// guideline of modifying one aggregate root per transaction.
    ///
    /// @param entities aggregate roots
    void addAll(Collection<T> entities);

    /// Modifies multiple existing aggregates in the aggregate set.
    /// <p>
    /// Batch semantics are sequential calls to {@link #modify}. <b>This method
    /// does not provide a transaction boundary</b>; completed writes are not
    /// rolled back when a later item fails. Any item with zero affected rows
    /// should cause {@link IllegalStateException}. Manage transactions explicitly
    /// in the application layer when atomicity is required.
    ///
    /// @param entities aggregate roots
    void modifyAll(Collection<T> entities);

    /// Removes an aggregate.
    /// <p>
    /// This is the recommended entry point for business deletion, either physical
    /// deletion or logical deletion through persistence-specific mechanisms. The
    /// caller should load the aggregate first and express deletion semantics,
    /// invariant checks, and domain event recording through aggregate behavior.
    /// The repository only persists removal. Zero affected rows should cause
    /// {@link IllegalStateException}. Infrastructure support may register the successfully removed
    /// aggregate for event dispatch at the application boundary.
    ///
    /// @param entity aggregate root
    void remove(T entity);
}
