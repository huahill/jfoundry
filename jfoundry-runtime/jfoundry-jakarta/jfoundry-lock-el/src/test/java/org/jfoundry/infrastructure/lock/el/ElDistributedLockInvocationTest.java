package org.jfoundry.infrastructure.lock.el;

import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.lock.DistributedLock;
import org.jfoundry.application.lock.LockCallback;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.application.lock.LockKey;
import org.jfoundry.application.lock.LockOptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElDistributedLockInvocationTest {

    @Test
    void evaluatesJakartaElKeysAndOptionalLease() throws Exception {
        RecordingLockExecutor lockExecutor = new RecordingLockExecutor();
        Method method = LockedService.class.getDeclaredMethod("handle", String.class);

        Object result = ElDistributedLockInvocation.invoke(
                new SimpleInvocation(new LockedService(), method, new Object[] {"42"}), lockExecutor);

        assertThat(result).isEqualTo("handled:42");
        assertThat(lockExecutor.key.scope()).isEqualTo(LockedService.class.getName() + "#handle");
        assertThat(lockExecutor.key.value()).isEqualTo("order:42");
        assertThat(lockExecutor.options.waitTime()).contains(java.time.Duration.ofSeconds(2));
        assertThat(lockExecutor.options.leaseTime()).contains(java.time.Duration.ofSeconds(5));
    }

    @Test
    void exposesIndexedArgumentsAndKeepsLiteralKeys() throws Exception {
        RecordingLockExecutor indexed = new RecordingLockExecutor();
        Method indexedMethod = LockedService.class.getDeclaredMethod("indexed", String.class);
        ElDistributedLockInvocation.invoke(
                new SimpleInvocation(new LockedService(), indexedMethod, new Object[] {"7"}), indexed);
        assertThat(indexed.key.value()).isEqualTo("p:7-a:7");

        RecordingLockExecutor literal = new RecordingLockExecutor();
        Method literalMethod = LockedService.class.getDeclaredMethod("literal");
        ElDistributedLockInvocation.invoke(
                new SimpleInvocation(new LockedService(), literalMethod, new Object[0]), literal);
        assertThat(literal.key.value()).isEqualTo("order#plain");
        assertThat(literal.options.leaseTime()).isEmpty();
    }

    @Test
    void rejectsBlankWaitTimeAndBlankResolvedKeys() throws Exception {
        Method blankWait = LockedService.class.getDeclaredMethod("blankWait");
        assertThatThrownBy(() -> ElDistributedLockInvocation.invoke(
                new SimpleInvocation(new LockedService(), blankWait, new Object[0]), new RecordingLockExecutor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("waitTime must not be blank");

        Method blankKey = LockedService.class.getDeclaredMethod("blankKey", String.class);
        assertThatThrownBy(() -> ElDistributedLockInvocation.invoke(
                new SimpleInvocation(new LockedService(), blankKey, new Object[] {" "}), new RecordingLockExecutor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Distributed lock key must not resolve to a blank value");
    }

    static class LockedService {

        @DistributedLock(key = "order:#{orderId}", waitTime = "2s", leaseTime = "5s")
        String handle(String orderId) {
            return "handled:" + orderId;
        }

        @DistributedLock(key = "p:#{p0}-a:#{a0}")
        String indexed(String orderId) {
            return orderId;
        }

        @DistributedLock(key = "order#plain")
        String literal() {
            return "literal";
        }

        @DistributedLock(key = "order", waitTime = "")
        String blankWait() {
            return "unused";
        }

        @DistributedLock(key = "#{orderId}")
        String blankKey(String orderId) {
            return orderId;
        }
    }

    static final class RecordingLockExecutor implements LockExecutor {

        private LockKey key;
        private LockOptions options;

        @Override
        public <T> T execute(LockKey key, LockOptions options, LockCallback<T> callback) throws Exception {
            this.key = key;
            this.options = options;
            return callback.execute();
        }
    }

    record SimpleInvocation(Object target, Method method, Object[] arguments) implements InvocationContext {

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public Object getTimer() {
            return null;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Constructor<?> getConstructor() {
            return null;
        }

        @Override
        public Object[] getParameters() {
            return arguments;
        }

        @Override
        public void setParameters(Object[] params) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> getContextData() {
            return Map.of();
        }

        @Override
        public Object proceed() throws Exception {
            try {
                return method.invoke(target, arguments);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
