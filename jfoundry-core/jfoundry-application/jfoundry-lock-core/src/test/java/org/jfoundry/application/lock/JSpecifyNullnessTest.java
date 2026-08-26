package org.jfoundry.application.lock;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void lockPackageAndSkippedExecutionContractAreAnnotated() throws NoSuchMethodException {
        assertTrue(LockExecutor.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(LockExecutor.class.getMethod("execute", LockKey.class, LockOptions.class, LockCallback.class)
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
