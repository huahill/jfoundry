package org.jfoundry.application.transaction;

/**
 * A transaction body that does not return a value.
 */
@FunctionalInterface
public interface TransactionAction {

    void execute();
}
