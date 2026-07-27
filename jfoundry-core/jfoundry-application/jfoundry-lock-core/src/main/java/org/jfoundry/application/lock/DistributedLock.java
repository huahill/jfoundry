package org.jfoundry.application.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks an application-layer method as requiring a distributed lock.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /// Lock value or runtime-specific expression.
    String key();

    /// Maximum time to wait for lock acquisition.
    String waitTime() default "0s";

    /// Lease time after which the lock expires automatically. Empty value delegates to the backend default.
    String leaseTime() default "";

    /// Behavior when the lock cannot be acquired.
    LockFailureMode failureMode() default LockFailureMode.THROW;
}
