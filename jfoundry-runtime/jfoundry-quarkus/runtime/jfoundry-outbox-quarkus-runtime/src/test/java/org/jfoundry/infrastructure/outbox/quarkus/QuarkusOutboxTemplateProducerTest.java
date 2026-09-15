package org.jfoundry.infrastructure.outbox.quarkus;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusOutboxTemplateProducerTest {

    @Test
    void createsTheDefaultJacksonPayloadSerializerWithoutAQuarkusJacksonBean() {
        assertThat(new QuarkusOutboxTemplateProducer().payloadSerializer()
                .serialize(new OrderCreatedV1("order-3")))
                .contains("order-3");
    }

    record OrderCreatedV1(String orderId) {
    }
}
