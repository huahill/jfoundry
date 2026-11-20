package io.huahill.jfoundry.application.lock;

/**
 * Callback executed while a distributed lock is held.
 *
 * @param <T> callback result type
 */
@FunctionalInterface
public interface LockCallback<T> {

    T execute() throws Exception;
}
