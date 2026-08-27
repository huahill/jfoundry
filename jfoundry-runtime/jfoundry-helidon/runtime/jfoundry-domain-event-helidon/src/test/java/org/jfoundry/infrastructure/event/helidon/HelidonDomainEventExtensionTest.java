package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.inject.Vetoed;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonDomainEventExtensionTest {

    @Test
    void isExcludedFromCdiManagedBeanDiscovery() {
        assertThat(HelidonDomainEventExtension.class.isAnnotationPresent(Vetoed.class)).isTrue();
    }
}
