package org.jfoundry.application.outbox;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void outboxPackageAndLifecycleContractsAreAnnotated() throws NoSuchMethodException {
        assertTrue(OutboxAppendRequest.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(OutboxMessage.class.isAnnotationPresent(NullUnmarked.class));
        assertTrue(OutboxMessage.class.getMethod("getPayloadKey")
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
