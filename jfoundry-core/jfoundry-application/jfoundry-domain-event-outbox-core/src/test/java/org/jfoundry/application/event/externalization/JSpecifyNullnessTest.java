package org.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JSpecifyNullnessTest {

    @Test
    void externalizationPackageAndOptionalRoutingMetadataAreAnnotated() {
        assertTrue(ExternalizedEvent.class.getPackage().isAnnotationPresent(NullMarked.class));
        assertTrue(Arrays.stream(ExternalizedEvent.class.getRecordComponents())
                .filter(component -> component.getName().equals("payloadKey"))
                .findFirst()
                .orElseThrow()
                .getAnnotatedType()
                .isAnnotationPresent(Nullable.class));
    }
}
