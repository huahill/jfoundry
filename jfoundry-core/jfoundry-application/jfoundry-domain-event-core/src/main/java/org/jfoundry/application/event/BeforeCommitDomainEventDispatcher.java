package org.jfoundry.application.event;

/// Marks a dispatcher whose work must complete before the enclosing transaction commits.
public interface BeforeCommitDomainEventDispatcher extends DomainEventDispatcher {
}
