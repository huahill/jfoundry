package org.jfoundry.infrastructure.event.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.event.CompositeDomainEventDispatcher;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;
import java.util.concurrent.CompletionStage;

/// Dispatches aggregate events after the outermost successful CDI application-service invocation.
@QuarkusDomainEventDispatch
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class QuarkusDomainEventDispatchInterceptor {

    private final QuarkusDomainEventScope scope;
    private final Instance<DomainEventDispatcher> dispatchers;

    @Inject
    public QuarkusDomainEventDispatchInterceptor(
            QuarkusDomainEventScope scope,
            @Any Instance<DomainEventDispatcher> dispatchers) {
        this.scope = scope;
        this.dispatchers = dispatchers;
    }

    @AroundInvoke
    Object dispatch(InvocationContext invocation) throws Exception {
        List<DomainEventDispatcher> delegates = dispatchers.stream().toList();
        return scope.invoke(delegates, outermost -> {
            try {
                Object result = invocation.proceed();
                if (isAsynchronousResult(result)) {
                    throw new UnsupportedOperationException(
                            "Quarkus domain-event dispatch supports synchronous application-service methods only");
                }
                if (outermost && !scope.failed()) {
                    dispatch(scope.drainEvents(), delegates);
                }
                return result;
            } catch (Exception exception) {
                scope.markFailed();
                throw exception;
            }
        });
    }

    private void dispatch(List<DomainEvent> events, List<DomainEventDispatcher> delegates) {
        if (events.isEmpty()) {
            return;
        }
        if (!delegates.isEmpty()) {
            new CompositeDomainEventDispatcher(delegates).dispatch(events);
        }
    }

    private static boolean isAsynchronousResult(Object result) {
        if (result instanceof CompletionStage<?>) {
            return true;
        }

        for (Class<?> type = result == null ? null : result.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().startsWith("io.smallrye.mutiny.")) {
                return true;
            }
        }
        return false;
    }
}
