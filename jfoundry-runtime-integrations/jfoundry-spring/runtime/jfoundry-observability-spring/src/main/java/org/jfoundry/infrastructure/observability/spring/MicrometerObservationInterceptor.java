package org.jfoundry.infrastructure.observability.spring;

import org.aopalliance.intercept.MethodInterceptor;

import java.util.Objects;

/// Spring AOP interceptor that observes JFoundry framework operations.
public final class MicrometerObservationInterceptor implements MethodInterceptor {

    private final MicrometerJFoundryObservability observability;

    public MicrometerObservationInterceptor(MicrometerJFoundryObservability observability) {
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
    }

    @Override
    public Object invoke(org.aopalliance.intercept.MethodInvocation invocation) throws Throwable {
        String operation = operationFor(invocation.getMethod().getName());
        return observability.observe(operation, result -> outcome(operation, result), invocation::proceed);
    }

    private static String operationFor(String methodName) {
        return switch (methodName) {
            case "append" -> MicrometerJFoundryObservability.OUTBOX_PERSIST;
            case "dispatch" -> MicrometerJFoundryObservability.OUTBOX_DISPATCH;
            case "executeOnce" -> MicrometerJFoundryObservability.INBOX_PROCESS;
            case "execute" -> MicrometerJFoundryObservability.LOCK_ACQUIRE;
            default -> throw new IllegalStateException("Unsupported JFoundry observation method: " + methodName);
        };
    }

    private static String outcome(String operation, Object result) {
        return switch (operation) {
            case MicrometerJFoundryObservability.INBOX_PROCESS ->
                    MicrometerJFoundryObservability.inboxOutcome((org.jfoundry.application.inbox.InboxExecutionResult) result);
            default -> MicrometerJFoundryObservability.SUCCESS;
        };
    }
}
