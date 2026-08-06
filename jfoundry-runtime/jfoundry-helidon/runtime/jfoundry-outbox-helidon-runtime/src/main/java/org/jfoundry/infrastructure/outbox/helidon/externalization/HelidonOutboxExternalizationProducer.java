package org.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizer;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer;
import tools.jackson.databind.json.JsonMapper;

/// Produces the overridable defaults used by Helidon Outbox externalization.
@Alternative
@Priority(1)
@Dependent
public final class HelidonOutboxExternalizationProducer {

    @Produces
    PayloadSerializer payloadSerializer() {
        return new JacksonPayloadSerializer(new JsonMapper());
    }

    @Produces
    ExternalizationRuleResolver externalizationRuleResolver() {
        return new ExternalizationRuleResolver();
    }

    @Produces
    AggregateRoutingResolver aggregateRoutingResolver() {
        return new AggregateRoutingResolver();
    }

    @Produces
    OutboxTemplate outboxTemplate(Instance<OutboxMessageStore> outboxMessageStore,
                                  Instance<PayloadSerializer> payloadSerializer) {
        return new OutboxTemplate(require(outboxMessageStore, "OutboxTemplate requires an OutboxMessageStore CDI bean"),
                require(payloadSerializer, "OutboxTemplate requires a PayloadSerializer CDI bean"));
    }

    @Produces
    DomainEventOutboxRecorder domainEventOutboxRecorder(Instance<OutboxMessageStore> outboxMessageStore,
                                                         Instance<PayloadSerializer> payloadSerializer,
                                                         ExternalizationRuleResolver ruleResolver,
                                                         AggregateRoutingResolver aggregateRoutingResolver,
                                                         @Any Instance<DomainEventExternalizer<?>> externalizers) {
        return new HelidonDomainEventOutboxRecorder(
                outboxMessageStore,
                payloadSerializer,
                ruleResolver,
                aggregateRoutingResolver,
                new DomainEventExternalizationResolver(externalizers.stream().toList()));
    }

    private static <T> T require(Instance<T> instance, String message) {
        if (!instance.isResolvable()) {
            throw new IllegalStateException(message);
        }
        return instance.get();
    }
}
