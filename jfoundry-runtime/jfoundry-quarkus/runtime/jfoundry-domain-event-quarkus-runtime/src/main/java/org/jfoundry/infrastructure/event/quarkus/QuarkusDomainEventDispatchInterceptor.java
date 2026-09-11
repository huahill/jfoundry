package org.jfoundry.infrastructure.event.quarkus;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.infrastructure.event.jta.JtaDomainEventDispatchSupport;

import java.util.concurrent.CompletionStage;

/// Dispatches aggregate events after the outermost successful CDI application-service invocation.
@QuarkusDomainEventDispatch
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class QuarkusDomainEventDispatchInterceptor {

    private final QuarkusDomainEventScope scope;
    private final DomainEventDispatchCoordinator coordinator;

    @Inject
    public QuarkusDomainEventDispatchInterceptor(
            QuarkusDomainEventScope scope,
            DomainEventDispatchCoordinator coordinator) {
        this.scope = scope;
        this.coordinator = coordinator;
    }

    @AroundInvoke
    Object dispatch(InvocationContext invocation) throws Exception {
        return JtaDomainEventDispatchSupport.invoke(
                scope.delegate(), coordinator, invocation::proceed,
                QuarkusDomainEventDispatchInterceptor::isAsynchronousResult, "Quarkus");
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
