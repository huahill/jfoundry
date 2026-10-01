package org.jfoundry.test;

import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.domain.repository.AggregateRepository;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/// In-memory repository for application tests.
/// <p>
/// Uses a {@link HashMap} to simulate an aggregate collection and provides the {@link #add},
/// {@link #modify}, and {@link #remove} collection operations. It leaves recorded events on the
/// aggregate so application-boundary test infrastructure can inspect or dispatch them.
/// <p>
/// Defensive contracts match the persistence implementation:
/// - {@link #add}: adding the same ID twice throws {@link IllegalStateException}, simulating a
///   primary-key conflict.
/// - {@link #modify}: modifying an ID that is not present throws {@link IllegalStateException}.
/// - {@link #remove}: removing an ID that is not present throws {@link IllegalStateException}.
///
/// @param <T>  aggregate root type
/// @param <ID> identifier type
public class InMemoryRepository<T extends AggregateRoot<T, ID> & EventRecordable, ID extends Identifier>
        implements AggregateRepository<T, ID> {

    private final Map<ID, T> entities = new HashMap<>();

    @Override
    public @Nullable T findById(ID id) {
        return entities.get(id);
    }

    @Override
    public void add(T entity) {
        ID id = entity.getId();
        if (entities.containsKey(id)) {
            throw new IllegalStateException("add failed — entity already exists: " + id);
        }
        entities.put(id, entity);
    }

    @Override
    public void modify(T entity) {
        ID id = entity.getId();
        if (!entities.containsKey(id)) {
            throw new IllegalStateException("modify failed — entity not found: " + id);
        }
        entities.put(id, entity);
    }

    @Override
    public void addAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (T entity : entities) {
            add(entity);
        }
    }

    @Override
    public void modifyAll(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (T entity : entities) {
            modify(entity);
        }
    }

    @Override
    public void remove(T entity) {
        ID id = entity.getId();
        if (!entities.containsKey(id)) {
            throw new IllegalStateException("remove failed — entity not found: " + id);
        }
        entities.remove(id);
    }

    public Optional<T> tryFind(ID id) {
        return Optional.ofNullable(entities.get(id));
    }

    public List<T> all() {
        return List.copyOf(entities.values());
    }

    public void clear() {
        entities.clear();
    }
}
