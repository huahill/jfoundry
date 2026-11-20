package io.huahill.jfoundry.infrastructure.outbox.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import io.huahill.jfoundry.application.messaging.PayloadSerializer;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.application.outbox.OutboxTemplate;
import io.huahill.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer;
import tools.jackson.databind.json.JsonMapper;

/// Produces the generic Outbox template and its default payload serializer.
@ApplicationScoped
public final class QuarkusOutboxTemplateProducer {

    @Produces
    @DefaultBean
    PayloadSerializer payloadSerializer() {
        return new JacksonPayloadSerializer(new JsonMapper());
    }

    @Produces
    @DefaultBean
    OutboxTemplate outboxTemplate(
            Instance<OutboxMessageStore> outboxMessageStore,
            PayloadSerializer payloadSerializer) {
        if (!outboxMessageStore.isResolvable()) {
            throw new IllegalStateException("OutboxTemplate requires an OutboxMessageStore CDI bean");
        }
        return new OutboxTemplate(outboxMessageStore.get(), payloadSerializer);
    }
}
