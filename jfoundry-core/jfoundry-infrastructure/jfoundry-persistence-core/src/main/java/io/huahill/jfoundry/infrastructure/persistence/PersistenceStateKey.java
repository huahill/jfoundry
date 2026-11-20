package io.huahill.jfoundry.infrastructure.persistence;

import java.util.Objects;

/// Identity-based typed key for persistence context state.
///
/// @param <S> state type
public final class PersistenceStateKey<S> {

    private final String name;
    private final Class<S> type;

    private PersistenceStateKey(String name, Class<S> type) {
        this.name = Objects.requireNonNull(name, "Persistence state key name must not be null.");
        this.type = Objects.requireNonNull(type, "Persistence state type must not be null.");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Persistence state key name must not be blank.");
        }
    }

    /// Creates a distinct key. Keys with the same name and type remain independent.
    public static <S> PersistenceStateKey<S> of(String name, Class<S> type) {
        return new PersistenceStateKey<>(name, type);
    }

    public String name() {
        return name;
    }

    public Class<S> type() {
        return type;
    }

    @Override
    public String toString() {
        return name + "<" + type.getSimpleName() + ">";
    }
}
