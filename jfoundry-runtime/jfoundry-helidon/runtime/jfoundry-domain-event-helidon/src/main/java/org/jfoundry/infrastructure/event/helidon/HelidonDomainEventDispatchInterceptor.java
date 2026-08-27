package org.jfoundry.infrastructure.event.helidon;

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
        return JtaDomainEventDispatchSupport.invoke(
                scope.delegate(), dispatchers.stream().toList(), invocation::proceed,
                result -> result instanceof CompletionStage<?>, "Helidon");
    }
}
