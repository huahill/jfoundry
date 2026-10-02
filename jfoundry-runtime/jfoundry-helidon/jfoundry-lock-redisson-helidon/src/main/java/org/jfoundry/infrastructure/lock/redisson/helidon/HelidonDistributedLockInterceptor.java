package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jfoundry.application.lock.LockExecutor;
import org.jfoundry.infrastructure.lock.el.ElDistributedLockInvocation;

/// Applies {@code @DistributedLock} through the shared Jakarta EL invocation.
@HelidonDistributedLock
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class HelidonDistributedLockInterceptor {

    private final LockExecutor lockExecutor;

    @Inject
    public HelidonDistributedLockInterceptor(LockExecutor lockExecutor) {
        this.lockExecutor = lockExecutor;
    }

    @AroundInvoke
    Object lock(InvocationContext invocation) throws Exception {
        return ElDistributedLockInvocation.invoke(invocation, lockExecutor);
    }
}
