package org.jfoundry.infrastructure.persistence;

import java.util.Optional;

/// Supplies the stable identifier of the actor associated with a persistence operation.
@FunctionalInterface
public interface AuditActorProvider {

    /// Returns the current actor identifier when an execution context has one.
    Optional<String> currentActorId();
}
