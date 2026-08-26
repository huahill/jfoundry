package org.jfoundry.application.inbox;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void inboxPackageAndLifecycleContractsAreAnnotated() throws NoSuchMethodException {
        assertTrue(InboxClaim.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(Arrays.stream(InboxClaim.class.getRecordComponents())
                .filter(component -> component.getName().equals("claimToken"))
                .findFirst()
                .orElseThrow()
                .getAnnotatedType()
                .isAnnotationPresent(Nullable.class));
        assertTrue(InboxMessage.class.isAnnotationPresent(NullUnmarked.class));
        assertTrue(InboxMessage.class.getMethod("getClaimToken")
                .getAnnotatedReturnType()
                .isAnnotationPresent(Nullable.class));
    }
}
