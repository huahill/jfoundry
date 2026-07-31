package org.jfoundry.infrastructure.event.helidon;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.jfoundry.application.event.CompositeDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

/// Dispatches aggregate events after the outermost successful CDI application-service invocation.
@HelidonDomainEventDispatch
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class HelidonDomainEventDispatchInterceptor {

    private final HelidonDomainEventScope scope;
    private final Instance<DomainEventDispatcher> dispatchers;

    @Inject
    public HelidonDomainEventDispatchInterceptor(HelidonDomainEventScope scope, @Any Instance<DomainEventDispatcher> dispatchers) {
        this.scope = scope;
        this.dispatchers = dispatchers;
    }

    @AroundInvoke
    Object dispatch(InvocationContext invocation) throws Exception {
        List<DomainEventDispatcher> delegates = dispatchers.stream().toList();
        return scope.invoke(delegates, outermost -> {
            try {
                Object result = invocation.proceed();
                if (result instanceof CompletionStage<?>) {
                    throw new UnsupportedOperationException(
                            "Helidon domain-event dispatch supports synchronous application-service methods only");
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

    private void dispatch(List<DomainEvent> events, List<DomainEventDispatcher> delegates,
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
}
