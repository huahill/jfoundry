package org.jfoundry.infrastructure.persistence;

import org.jfoundry.domain.repository.AggregateRepository;
import org.jspecify.annotations.Nullable;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

import java.util.Collection;
import java.util.List;

/// Storage-neutral lifecycle base for aggregate repository adapters.
/// <p>
/// Subclasses implement complete aggregate persistence operations. A complete operation may use
/// one data object, multiple tables, multiple mappers, or another storage model. Public lifecycle
/// methods provide the supported lifecycle entry points, while protected {@code do*} methods are
/// the persistence-specific extension points. Lifecycle methods remain non-final so runtime
/// frameworks can create class-based proxies for transactions and other cross-cutting concerns.
///
/// @param <T> aggregate root type
/// @param <ID> aggregate identifier type
public abstract class AbstractAggregateRepository<
        T extends AggregateRoot<T, ID>,
        ID extends Identifier>
        extends AbstractPersistenceAdapter
        implements AggregateRepository<T, ID>, AggregatePersistenceObserverAware {

    private @Nullable AggregatePersistenceObserver persistenceObserver;

    /// Injects the observer used to observe successfully persisted aggregates.
    @Override
    public final void setAggregatePersistenceObserver(AggregatePersistenceObserver observer) {
        persistenceObserver = java.util.Objects.requireNonNull(
                observer, "AggregatePersistenceObserver must not be null.");
    }

    /// Loads and restores one complete aggregate, returning null when it does not exist.
    protected abstract T doFindById(ID id);

    /// Persists one complete new aggregate.
    protected abstract void doAdd(T aggregate);

    /// Persists all changes to one complete existing aggregate.
    protected abstract void doModify(T aggregate);

    /// Removes one complete aggregate according to the adapter's storage semantics.
    protected abstract void doRemove(T aggregate);

    @Override
    public T findById(ID id) {
        if (id == null) {
            throw new IllegalArgumentException("Aggregate id must not be null.");
        }
        return find(() -> doFindById(id));
    }

    @Override
    public void add(T aggregate) {
        T validatedAggregate = requireAggregate(aggregate);
        add(() -> doAdd(validatedAggregate));
        notifyAfterPersisted(validatedAggregate);
    }

    @Override
    public void modify(T aggregate) {
        T validatedAggregate = requireAggregate(aggregate);
        modify(() -> doModify(validatedAggregate));
        notifyAfterPersisted(validatedAggregate);
    }

    @Override
    public void addAll(Collection<T> aggregates) {
        List<T> aggregateList = requireAggregates(aggregates);
        aggregateList.forEach(aggregate ->
                add(() -> doAdd(aggregate)));
        aggregateList.forEach(this::notifyAfterPersisted);
    }

    @Override
    public void modifyAll(Collection<T> aggregates) {
        List<T> aggregateList = requireAggregates(aggregates);
        aggregateList.forEach(aggregate ->
                modify(() -> doModify(aggregate)));
        aggregateList.forEach(this::notifyAfterPersisted);
    }

    @Override
    public void remove(T aggregate) {
        T validatedAggregate = requireAggregate(aggregate);
        if (validatedAggregate.getId() == null) {
            throw new IllegalArgumentException("Aggregate id must not be null.");
        }
        remove(() -> doRemove(validatedAggregate));
        notifyAfterPersisted(validatedAggregate);
    }

    private T requireAggregate(T aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("Aggregate must not be null.");
        }
        return aggregate;
    }

    private List<T> requireAggregates(Collection<T> aggregates) {
        if (aggregates == null) {
            throw new IllegalArgumentException("Aggregates must not be null.");
        }
        if (aggregates.isEmpty()) {
            return List.of();
        }
        for (T aggregate : aggregates) {
            requireAggregate(aggregate);
        }
        return List.copyOf(aggregates);
    }

    private void notifyAfterPersisted(T aggregate) {
        if (persistenceObserver != null) {
            persistenceObserver.afterPersisted(aggregate);
        }
    }

}
