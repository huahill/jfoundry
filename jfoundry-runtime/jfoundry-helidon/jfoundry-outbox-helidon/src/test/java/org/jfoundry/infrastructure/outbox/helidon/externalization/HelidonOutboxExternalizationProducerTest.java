package org.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelidonOutboxExternalizationProducerTest {

    @Test
    void exposesReplaceableCdiDefaultsWithoutApplicationScopedNativeProxies() {
        assertThat(HelidonOutboxExternalizationProducer.class.isAnnotationPresent(Alternative.class)).isTrue();
        assertThat(HelidonOutboxExternalizationProducer.class.isAnnotationPresent(Priority.class)).isTrue();
    }

    @Test
    void createsTheDefaultJacksonPayloadSerializer() {
        assertThat(new HelidonOutboxExternalizationProducer().payloadSerializer().serialize(new Event("order-1")))
                .contains("order-1");
    }

    @Test
    void applicationPayloadSerializerReplacesTheEnabledDefaultProducer() {
        String previous = System.getProperty("mp.initializer.allow");
        System.setProperty("mp.initializer.allow", "true");
        try {
            try (SeContainer container = SeContainerInitializer.newInstance()
                    .disableDiscovery()
                    .addBeanClasses(HelidonOutboxExternalizationProducer.class, ApplicationPayloadSerializer.class)
                    .initialize()) {
                assertThat(container.select(PayloadSerializer.class).get())
                        .isInstanceOf(ApplicationPayloadSerializer.class);
            }
        } finally {
            if (previous == null) {
                System.clearProperty("mp.initializer.allow");
            } else {
                System.setProperty("mp.initializer.allow", previous);
            }
        }
    }

    private record Event(String orderId) {
    }

    @Dependent
    @Alternative
    @Priority(2)
    static final class ApplicationPayloadSerializer implements PayloadSerializer {

        @Override
        public String serialize(Object payload) {
            return "application";
        }
    }
}
