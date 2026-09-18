package org.jfoundry.domain.exception;

/**
 * Indicates that the current domain object state does not allow the requested behavior.
 * <p>
 * Throw this from aggregate, entity, or value-object behavior when an operation is valid in general but forbidden by the
 * receiver's current lifecycle or state. Do not use this for rule checks independent of the object's current state,
 * request parsing, application argument validation, optimistic locking, persistence failures, or infrastructure/runtime
 * failures.
 */
public class DomainStateException extends DomainException {

    public DomainStateException(String message) {
        super(message);
    }

    public DomainStateException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Creates a domain-state failure identified by a stable message code with interpolation arguments.
    /// The code is resolved to caller-facing text at the HTTP boundary through message catalogs.
    public DomainStateException(String code, Object... args) {
        super(code, args);
    }
}
