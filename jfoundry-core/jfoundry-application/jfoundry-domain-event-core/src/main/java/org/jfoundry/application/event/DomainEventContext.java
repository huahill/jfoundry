package org.jfoundry.application.event;

import org.jfoundry.domain.event.EventRecordable;

/**
 * Registers aggregates touched within an application-service boundary.
 */
public interface DomainEventContext {

    void register(EventRecordable aggregate);
}
