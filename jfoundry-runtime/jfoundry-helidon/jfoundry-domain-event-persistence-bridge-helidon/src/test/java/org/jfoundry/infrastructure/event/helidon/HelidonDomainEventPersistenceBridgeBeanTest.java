package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonDomainEventPersistenceBridgeBeanTest {

    @Test
    void usesDependentScopeAndConstructorInjection() {
        assertThat(HelidonDomainEventPersistenceBridgeBinder.class.isAnnotationPresent(Dependent.class)).isTrue();
        assertThat(Arrays.stream(HelidonDomainEventPersistenceBridgeBinder.class.getDeclaredConstructors())
                .anyMatch(constructor -> constructor.isAnnotationPresent(Inject.class)))
                .isTrue();
    }
}
