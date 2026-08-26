package org.jfoundry.infrastructure.event.quarkus;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jfoundry.infrastructure.event.jta.JtaDomainEventDispatchSupport;

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
        return JtaDomainEventDispatchSupport.invoke(
                scope.delegate(), dispatchers.stream().toList(), invocation::proceed,
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
