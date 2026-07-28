package org.jfoundry.application.event.externalization;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/// Maps one domain-event type to versioned integration-message contracts.
///
/// @param <E> source domain-event type
public interface DomainEventExternalizer<E extends DomainEvent> {

    /// Returns the source domain-event type handled by this externalizer.
    Class<E> sourceEventType();

    /// Maps the supplied domain event to zero or more integration-message descriptions.
    List<ExternalizedEvent> externalize(E event);
}
