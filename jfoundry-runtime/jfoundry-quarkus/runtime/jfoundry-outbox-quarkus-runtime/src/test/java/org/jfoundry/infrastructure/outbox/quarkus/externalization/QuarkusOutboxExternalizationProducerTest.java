package org.jfoundry.infrastructure.outbox.quarkus.externalization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusOutboxExternalizationProducerTest {

    @Test
    void createsTheDefaultJacksonThreePayloadSerializerWithoutAQuarkusJacksonBean() {
        assertThat(new QuarkusOutboxExternalizationProducer().payloadSerializer().serialize(new OrderCreatedV1("order-3")))
                .contains("order-3");
    }

    record OrderCreatedV1(String orderId) {
    }
}
