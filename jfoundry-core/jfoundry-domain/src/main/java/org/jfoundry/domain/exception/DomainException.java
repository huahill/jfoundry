package org.jfoundry.domain.exception;

/**
 * Base exception for expected domain-model failures.
 * <p>
 * Use this hierarchy for failures discovered while executing domain behavior, such as a rule that cannot be satisfied
 * or a domain object state that rejects the requested behavior. Do not use it for malformed transport input,
 * application-use-case argument validation, missing application data, persistence conflicts, or infrastructure/runtime
 * failures.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
