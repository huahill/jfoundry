package org.jfoundry.application.event;

import org.jmolecules.event.types.DomainEvent;

import java.util.List;

/**
 * Application-layer contract for batch domain event dispatch.
 */
public interface DomainEventDispatcher {

    void dispatch(List<? extends DomainEvent> events);
}
