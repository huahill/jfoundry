package org.jfoundry.application.transaction;

/**
 * A transaction body that returns a value.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    T execute();
}
