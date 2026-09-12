package org.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxExternalizationProducerTest {

    @Test
    void exposesReplaceableCdiDefaultsWithoutApplicationScopedNativeProxies() {
        assertThat(HelidonOutboxExternalizationProducer.class.isAnnotationPresent(Alternative.class)).isTrue();
        assertThat(HelidonOutboxExternalizationProducer.class.isAnnotationPresent(Priority.class)).isTrue();
    }

}
