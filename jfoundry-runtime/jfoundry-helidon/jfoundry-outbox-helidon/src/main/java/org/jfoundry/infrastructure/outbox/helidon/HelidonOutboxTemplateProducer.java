package org.jfoundry.infrastructure.outbox.helidon;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer;
import tools.jackson.databind.json.JsonMapper;

/// Produces the generic Outbox template and its default payload serializer.
@Alternative
@Priority(1)
@Dependent
public final class HelidonOutboxTemplateProducer {

    @Produces
    PayloadSerializer payloadSerializer() {
        return new JacksonPayloadSerializer(new JsonMapper());
    }

    @Produces
    OutboxTemplate outboxTemplate(Instance<OutboxMessageStore> outboxMessageStore,
                                  Instance<PayloadSerializer> payloadSerializer) {
        return new OutboxTemplate(
                require(outboxMessageStore, "OutboxTemplate requires an OutboxMessageStore CDI bean"),
                require(payloadSerializer, "OutboxTemplate requires a PayloadSerializer CDI bean"));
    }

    private static <T> T require(Instance<T> instance, String message) {
        if (!instance.isResolvable()) {
            throw new IllegalStateException(message);
        }
        return instance.get();
    }
}
