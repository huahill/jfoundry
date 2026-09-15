package org.jfoundry.infrastructure.event.jta;

import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Coordinates synchronous application-service invocations with a JTA domain-event scope.
public final class JtaDomainEventDispatchSupport {

    private JtaDomainEventDispatchSupport() {
    }

    /// Invokes one application-service operation and dispatches its recorded events at the correct phase.
    public static Object invoke(
            JtaDomainEventScope scope,
            DomainEventDispatchCoordinator coordinator,
            Invocation invocation,
            Predicate<Object> asynchronousResult,
            String runtimeName) throws Exception {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(coordinator, "coordinator must not be null");
        Objects.requireNonNull(invocation, "invocation must not be null");
        Objects.requireNonNull(asynchronousResult, "asynchronousResult must not be null");
        Objects.requireNonNull(runtimeName, "runtimeName must not be null");

        return scope.invoke(coordinator, outermost -> {
            try {
                Object result = invocation.proceed();
                if (asynchronousResult.test(result)) {
                    throw new UnsupportedOperationException(
                            runtimeName + " domain-event dispatch supports synchronous application-service methods only");
                }
                if (outermost && !scope.failed()) {
                    if (scope.hasTransactionEvents()) {
                        scope.dispatchBeforeCommit();
                    } else {
                        List<DomainEvent> events = scope.drainEvents();
                        if (!events.isEmpty()) {
                            coordinator.dispatchWithoutTransaction(events);
                        }
                    }
                }
                return result;
            } catch (Exception exception) {
                scope.markFailed();
                throw exception;
            }
        });
    }

    /// Application-service invocation that may throw a checked exception.
    @FunctionalInterface
    public interface Invocation {

        /// Proceeds with the invocation.
        Object proceed() throws Exception;
    }
}
