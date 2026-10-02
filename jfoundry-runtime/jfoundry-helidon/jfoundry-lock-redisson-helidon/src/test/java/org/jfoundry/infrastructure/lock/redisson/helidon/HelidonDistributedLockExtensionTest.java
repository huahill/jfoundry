package org.jfoundry.infrastructure.lock.redisson.helidon;

import jakarta.enterprise.inject.Vetoed;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonDistributedLockExtensionTest {

    @Test
    void isExcludedFromCdiManagedBeanDiscovery() {
        assertThat(HelidonDistributedLockExtension.class.isAnnotationPresent(Vetoed.class)).isTrue();
    }
}
