package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.enterprise.context.Dependent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxTriggerCdiScopeTest {

    @Test
    void usesDependentScopeForNativeCdiCompatibility() {
        assertThat(HelidonOutboxTrigger.class.isAnnotationPresent(Dependent.class)).isTrue();
    }

    @Test
    void doesNotImplementOutboxDispatcher() {
        assertThat(org.jfoundry.application.outbox.OutboxDispatcher.class.isAssignableFrom(HelidonOutboxTrigger.class))
                .isFalse();
    }
}
