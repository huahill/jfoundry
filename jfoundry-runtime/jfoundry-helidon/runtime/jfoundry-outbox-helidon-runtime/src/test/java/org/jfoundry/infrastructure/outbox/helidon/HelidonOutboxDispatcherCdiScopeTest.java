package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.enterprise.context.Dependent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxDispatcherCdiScopeTest {

    @Test
    void usesDependentScopeForNativeCdiCompatibility() {
        assertThat(HelidonOutboxDispatcher.class.isAnnotationPresent(Dependent.class)).isTrue();
    }
}
