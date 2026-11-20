package io.huahill.jfoundry.infrastructure.outbox.helidon.externalization;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import io.huahill.jfoundry.application.event.externalization.AggregateRoutingResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizer;
import io.huahill.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import io.huahill.jfoundry.application.messaging.PayloadSerializer;
import io.huahill.jfoundry.application.event.outbox.DefaultDomainEventOutboxRecorder;
import io.huahill.jfoundry.application.event.outbox.DomainEventOutboxRecorder;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;

/// Produces the overridable defaults used by Helidon Domain Event Outbox composition.
@Alternative
@Priority(1)
@Dependent
public final class HelidonOutboxExternalizationProducer {

    @Produces
    ExternalizationRuleResolver externalizationRuleResolver() {
        return new ExternalizationRuleResolver();
    }

    @Produces
    AggregateRoutingResolver aggregateRoutingResolver() {
        return new AggregateRoutingResolver();
    }

    @Produces
    DomainEventOutboxRecorder domainEventOutboxRecorder(Instance<OutboxMessageStore> outboxMessageStore,
                                                         Instance<PayloadSerializer> payloadSerializer,
                                                         ExternalizationRuleResolver ruleResolver,
                                                         AggregateRoutingResolver aggregateRoutingResolver,
                                                         @Any Instance<DomainEventExternalizer<?>> externalizers) {
        return new DefaultDomainEventOutboxRecorder(
                () -> require(outboxMessageStore, "Automatic domain-event externalization requires an OutboxMessageStore CDI bean"),
                () -> require(payloadSerializer, "Automatic domain-event externalization requires a PayloadSerializer CDI bean"),
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
