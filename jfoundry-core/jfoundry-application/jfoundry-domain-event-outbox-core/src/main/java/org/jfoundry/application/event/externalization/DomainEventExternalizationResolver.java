package org.jfoundry.application.event.externalization;

import org.jmolecules.event.types.DomainEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Resolves application-provided integration-message mappings for domain events.
public final class DomainEventExternalizationResolver {

    private final List<DomainEventExternalizer<?>> externalizers;

    public DomainEventExternalizationResolver(List<? extends DomainEventExternalizer<?>> externalizers) {
        Objects.requireNonNull(externalizers, "externalizers must not be null");
        this.externalizers = List.copyOf(externalizers);
        this.externalizers.forEach(externalizer -> Objects.requireNonNull(
                externalizer.sourceEventType(), "sourceEventType must not be null"));
    }

    /// Returns an empty optional when no externalizer handles the event.
    /// A present empty list means an externalizer matched and deliberately suppressed direct externalization.
    public Optional<List<ExternalizedEvent>> resolve(DomainEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        List<ExternalizedEvent> events = new ArrayList<>();
        boolean matched = false;
        for (DomainEventExternalizer<?> externalizer : externalizers) {
            if (externalizer.sourceEventType().isAssignableFrom(event.getClass())) {
                matched = true;
                events.addAll(externalize(externalizer, event));
            }
        }
        return matched ? Optional.of(List.copyOf(events)) : Optional.empty();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ExternalizedEvent> externalize(DomainEventExternalizer externalizer, DomainEvent event) {
        return Objects.requireNonNull(externalizer.externalize(event), "externalize must not return null");
    }
}
