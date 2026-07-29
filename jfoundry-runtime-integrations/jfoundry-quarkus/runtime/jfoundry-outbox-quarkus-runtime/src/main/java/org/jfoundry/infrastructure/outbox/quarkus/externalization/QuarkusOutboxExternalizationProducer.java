package org.jfoundry.infrastructure.outbox.quarkus.externalization;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
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

/// Produces the replaceable defaults used by Quarkus Outbox externalization.
@ApplicationScoped
public final class QuarkusOutboxExternalizationProducer {

    @Produces
    @DefaultBean
    PayloadSerializer payloadSerializer() {
        return new JacksonPayloadSerializer(new JsonMapper());
    }

    @Produces
    @DefaultBean
    ExternalizationRuleResolver externalizationRuleResolver() {
        return new ExternalizationRuleResolver();
    }

    @Produces
    @DefaultBean
    AggregateRoutingResolver aggregateRoutingResolver() {
        return new AggregateRoutingResolver();
    }

    @Produces
    @DefaultBean
    OutboxTemplate outboxTemplate(Instance<OutboxMessageStore> outboxMessageStore, PayloadSerializer payloadSerializer) {
        if (!outboxMessageStore.isResolvable()) {
            throw new IllegalStateException("OutboxTemplate requires an OutboxMessageStore CDI bean");
        }
        return new OutboxTemplate(outboxMessageStore.get(), payloadSerializer);
    }

    @Produces
    @DefaultBean
    DomainEventOutboxRecorder domainEventOutboxRecorder(
            Instance<OutboxMessageStore> outboxMessageStore,
            PayloadSerializer payloadSerializer,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver,
            @Any Instance<DomainEventExternalizer<?>> externalizers) {
        return new DefaultDomainEventOutboxRecorder(
                outboxMessageStore,
                payloadSerializer,
                ruleResolver,
                aggregateRoutingResolver,
                new DomainEventExternalizationResolver(externalizers.stream().toList()));
    }
}
