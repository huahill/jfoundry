package org.jfoundry.infrastructure.event.helidon;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;
import org.jfoundry.infrastructure.event.jta.JtaDomainEventDispatchSupport;

import java.util.concurrent.CompletionStage;

/// Dispatches aggregate events after the outermost successful CDI application-service invocation.
@HelidonDomainEventDispatch
@Interceptor
@Priority(Interceptor.Priority.APPLICATION + 100)
public class HelidonDomainEventDispatchInterceptor {

    private final HelidonDomainEventScope scope;
    private final DomainEventDispatchCoordinator coordinator;

    @Inject
    public HelidonDomainEventDispatchInterceptor(HelidonDomainEventScope scope, DomainEventDispatchCoordinator coordinator) {
        this.scope = scope;
        this.coordinator = coordinator;
    }

    @AroundInvoke
    Object dispatch(InvocationContext invocation) throws Exception {
        return JtaDomainEventDispatchSupport.invoke(
                scope.delegate(), coordinator, invocation::proceed,
                result -> result instanceof CompletionStage<?>, "Helidon");
    }
}
