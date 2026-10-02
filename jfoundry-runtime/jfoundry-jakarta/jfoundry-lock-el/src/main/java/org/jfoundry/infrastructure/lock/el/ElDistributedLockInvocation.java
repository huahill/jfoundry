package org.jfoundry.infrastructure.lock.el;

import jakarta.el.ELManager;
import jakarta.el.ELProcessor;
import jakarta.el.ValueExpression;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.lock.DistributedLock;
import org.jfoundry.application.lock.LockDurations;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/// Evaluates a {@link DistributedLock} annotation and executes it through {@link LockExecutor}.
public final class ElDistributedLockInvocation {

    private ElDistributedLockInvocation() {
    }

    /// Invokes the intercepted method while holding the lock described by its annotation.
    public static Object invoke(InvocationContext invocation, LockExecutor lockExecutor) throws Exception {
        Method method = invocation.getMethod();
        DistributedLock annotation = method.getAnnotation(DistributedLock.class);
        if (annotation == null) {
            return invocation.proceed();
        }
        LockOptions.Builder options = LockOptions.builder()
                .waitTime(LockDurations.required(annotation.waitTime(), "waitTime"))
                .failureMode(annotation.failureMode());
        LockDurations.optional(annotation.leaseTime(), "leaseTime").ifPresent(options::leaseTime);
        return lockExecutor.execute(resolveKey(annotation.key(), invocation), options.build(), invocation::proceed);
    }

    private static LockKey resolveKey(String keyExpression, InvocationContext invocation) {
        if (keyExpression == null || keyExpression.isBlank()) {
            throw new IllegalArgumentException("Distributed lock key must not be blank");
        }
        String candidate = keyExpression.trim();
        String resolved = isExpression(candidate) ? evaluate(candidate, invocation) : candidate;
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("Distributed lock key must not resolve to a blank value");
        }
        Method method = invocation.getMethod();
        return new LockKey(method.getDeclaringClass().getName() + "#" + method.getName(), resolved);
    }

    private static boolean isExpression(String value) {
        return value.contains("${") || value.contains("#{");
    }

    private static String evaluate(String expression, InvocationContext invocation) {
        ELProcessor processor = new ELProcessor();
        Object[] arguments = invocation.getParameters();
        Parameter[] parameters = invocation.getMethod().getParameters();
        for (int index = 0; index < arguments.length; index++) {
            processor.defineBean("p" + index, arguments[index]);
            processor.defineBean("a" + index, arguments[index]);
            if (index < parameters.length && parameters[index].isNamePresent()) {
                processor.defineBean(parameters[index].getName(), arguments[index]);
            }
        }
        ELManager manager = processor.getELManager();
        ValueExpression value = manager.getExpressionFactory()
                .createValueExpression(manager.getELContext(), expression, Object.class);
        Object result = value.getValue(manager.getELContext());
        return result == null ? null : result.toString();
    }
}
