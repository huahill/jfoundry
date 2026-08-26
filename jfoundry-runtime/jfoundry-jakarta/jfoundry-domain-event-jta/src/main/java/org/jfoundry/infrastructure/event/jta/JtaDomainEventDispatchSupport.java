package org.jfoundry.infrastructure.event.jta;

import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.CompositeDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventDispatcher;
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
            List<DomainEventDispatcher> dispatchers,
            Invocation invocation,
            Predicate<Object> asynchronousResult,
            String runtimeName) throws Exception {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(dispatchers, "dispatchers must not be null");
        Objects.requireNonNull(invocation, "invocation must not be null");
        Objects.requireNonNull(asynchronousResult, "asynchronousResult must not be null");
        Objects.requireNonNull(runtimeName, "runtimeName must not be null");

        List<DomainEventDispatcher> delegates = List.copyOf(dispatchers);
        return scope.invoke(delegates, outermost -> {
            try {
                Object result = invocation.proceed();
                if (asynchronousResult.test(result)) {
                    throw new UnsupportedOperationException(
                            runtimeName + " domain-event dispatch supports synchronous application-service methods only");
                }
                if (outermost && !scope.failed()) {
                    dispatch(scope.drainEvents(), delegates, scope.hasTransactionEvents());
                }
                return result;
            } catch (Exception exception) {
                scope.markFailed();
                throw exception;
            }
        });
    }

    private static void dispatch(
            List<DomainEvent> events,
            List<DomainEventDispatcher> delegates,
            boolean transactional) {
        if (events.isEmpty()) {
            return;
        }
        if (transactional) {
            delegates.stream()
                    .filter(BeforeCommitDomainEventDispatcher.class::isInstance)
                    .forEach(dispatcher -> dispatcher.dispatch(events));
        } else if (!delegates.isEmpty()) {
            new CompositeDomainEventDispatcher(delegates).dispatch(events);
        }
    }

    /// Application-service invocation that may throw a checked exception.
    @FunctionalInterface
    public interface Invocation {

        /// Proceeds with the invocation.
        Object proceed() throws Exception;
    }
}
