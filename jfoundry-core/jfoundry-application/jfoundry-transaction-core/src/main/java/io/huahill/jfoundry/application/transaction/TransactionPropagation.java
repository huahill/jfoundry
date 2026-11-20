package io.huahill.jfoundry.application.transaction;

/**
 * Common transaction propagation modes that can be mapped by runtime adapters.
 */
public enum TransactionPropagation {

    REQUIRED,
    REQUIRES_NEW,
    SUPPORTS,
    MANDATORY,
    NOT_SUPPORTED,
    NEVER
}
