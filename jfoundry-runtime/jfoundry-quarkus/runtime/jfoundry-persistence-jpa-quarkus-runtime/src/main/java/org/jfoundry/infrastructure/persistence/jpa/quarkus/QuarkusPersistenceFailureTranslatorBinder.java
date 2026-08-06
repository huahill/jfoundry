package org.jfoundry.infrastructure.persistence.jpa.quarkus;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jfoundry.infrastructure.persistence.AbstractPersistenceAdapter;
import org.jfoundry.infrastructure.persistence.PersistenceFailureTranslator;

import java.util.Objects;

/// Supplies the CDI persistence failure translator to Quarkus persistence adapters.
public final class QuarkusPersistenceFailureTranslatorBinder {

    private final PersistenceFailureTranslator persistenceFailureTranslator;
    private final Instance<AbstractPersistenceAdapter> persistenceAdapters;

    @Inject
    public QuarkusPersistenceFailureTranslatorBinder(
            PersistenceFailureTranslator persistenceFailureTranslator,
            Instance<AbstractPersistenceAdapter> persistenceAdapters) {
        this.persistenceFailureTranslator = Objects.requireNonNull(
                persistenceFailureTranslator, "Persistence failure translator must not be null.");
        this.persistenceAdapters = Objects.requireNonNull(
                persistenceAdapters, "Persistence adapters must not be null.");
    }

    void initialize(@Observes StartupEvent event) {
        persistenceAdapters.forEach(
                adapter -> adapter.setPersistenceFailureTranslator(persistenceFailureTranslator));
    }
}
