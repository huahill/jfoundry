package org.jfoundry.autoconfigure.event;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jfoundry.application.event.DomainEventDispatchCoordinator;

public class DomainEventDispatchInterceptor implements MethodInterceptor {

    private final DomainEventScope scope;
    private final DomainEventDispatchCoordinator coordinator;

    public DomainEventDispatchInterceptor(DomainEventScope scope,
                                          DomainEventDispatchCoordinator coordinator) {
        this.scope = scope;
        this.coordinator = coordinator;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return scope.invoke(coordinator, outermost -> {
            try {
                Object result = invocation.proceed();
                if (outermost && !scope.failed() && !scope.hasTransactionEvents()) {
                    var events = scope.drainEvents();
                    if (!events.isEmpty()) {
                        coordinator.dispatchWithoutTransaction(events);
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
