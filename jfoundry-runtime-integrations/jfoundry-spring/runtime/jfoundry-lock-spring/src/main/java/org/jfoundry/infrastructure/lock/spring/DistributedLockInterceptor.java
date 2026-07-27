package org.jfoundry.infrastructure.lock.spring;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jfoundry.application.lock.DistributedLock;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/// Spring AOP interceptor that executes {@link DistributedLock} methods through {@link LockExecutor}.
public class DistributedLockInterceptor implements MethodInterceptor {

    private final LockExecutor lockExecutor;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public DistributedLockInterceptor(LockExecutor lockExecutor) {
        this.lockExecutor = Objects.requireNonNull(lockExecutor, "lockExecutor must not be null");
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        DistributedLock annotation = invocation.getMethod().getAnnotation(DistributedLock.class);
        if (annotation == null) {
            return invocation.proceed();
        }
        LockKey key = resolveKey(annotation.key(), invocation);
        LockOptions options = LockOptions.builder()
                .waitTime(parseDuration(annotation.waitTime(), "waitTime"))
                .leaseTime(parseOptionalDuration(annotation.leaseTime(), "leaseTime"))
                .failureMode(annotation.failureMode())
                .build();
        try {
            return lockExecutor.execute(key, options, () -> {
                try {
                    return invocation.proceed();
                } catch (Exception ex) {
                    throw ex;
                } catch (Throwable ex) {
                    throw new ThrowableInvocationException(ex);
                }
            });
        } catch (ThrowableInvocationException ex) {
            throw ex.getCause();
        }
    }

    private LockKey resolveKey(String keyExpression, MethodInvocation invocation) {
        if (keyExpression == null || keyExpression.isBlank()) {
            throw new IllegalArgumentException("Distributed lock key must not be blank");
        }
        String candidate = keyExpression.trim();
        if (!looksLikeSpelExpression(candidate)) {
            return new LockKey(defaultScope(invocation.getMethod()), candidate);
        }
        Object value = expressionParser.parseExpression(candidate).getValue(evaluationContext(invocation));
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Distributed lock key must not resolve to a blank value");
        }
        return new LockKey(defaultScope(invocation.getMethod()), value.toString());
    }

    private static String defaultScope(Method method) {
        return method.getDeclaringClass().getName() + "#" + method.getName();
    }

    private static boolean looksLikeSpelExpression(String value) {
        return value.contains("#") || value.startsWith("'") || value.startsWith("\"")
                || value.startsWith("@") || value.contains("T(");
    }

    private EvaluationContext evaluationContext(MethodInvocation invocation) {
        StandardEvaluationContext context = new StandardEvaluationContext(invocation.getThis());
        Object[] arguments = invocation.getArguments();
        Method method = invocation.getMethod();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < arguments.length; i++) {
            context.setVariable("p" + i, arguments[i]);
            context.setVariable("a" + i, arguments[i]);
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], arguments[i]);
            }
        }
        return context;
    }

    private static Duration parseOptionalDuration(String value, String attributeName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDuration(value, attributeName);
    }

    private static Duration parseDuration(String value, String attributeName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(attributeName + " must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("p")) {
            return Duration.parse(value);
        }
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        return Duration.ofMillis(Long.parseLong(normalized));
    }

    private static final class ThrowableInvocationException extends RuntimeException {

        private ThrowableInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
