package org.jfoundry.autoconfigure.event;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jfoundry.application.event.DomainEventDispatcher;
import org.jmolecules.event.types.DomainEvent;

import java.util.List;

public class DomainEventDispatchInterceptor implements MethodInterceptor {

    private final DomainEventScope scope;
    private final DomainEventDispatcher dispatcher;

    public DomainEventDispatchInterceptor(DomainEventScope scope,
                                          DomainEventDispatcher dispatcher) {
        this.scope = scope;
        this.dispatcher = dispatcher;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return scope.invoke(outermost -> {
            try {
                Object result = invocation.proceed();
                if (outermost && !scope.failed()) {
                    List<DomainEvent> events = scope.drainEvents();
                    if (!events.isEmpty()) {
                        dispatcher.dispatch(events);
                    }
                }
                return result;
            } catch (Throwable ex) {
                scope.markFailed();
                throw ex;
            }
        });
    }
}
