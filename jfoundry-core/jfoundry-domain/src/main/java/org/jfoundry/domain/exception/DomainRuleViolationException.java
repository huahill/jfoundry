package org.jfoundry.domain.exception;

/**
 * Indicates that a domain rule cannot be satisfied.
 * <p>
 * Throw this from domain model behavior or domain services when the requested behavior is meaningful, but a business
 * rule, policy, invariant, or {@link org.jfoundry.domain.specification.Specification} rejects it. Prefer a
 * domain-specific subtype or message when the rule has a stable ubiquitous-language name. Do not use this for request
 * parsing, application argument validation, missing application data, persistence failures, or lifecycle-state conflicts
 * better represented by {@link DomainStateException}.
 */
public class DomainRuleViolationException extends DomainException {

    public DomainRuleViolationException(String message) {
        super(message);
    }

    public DomainRuleViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}
