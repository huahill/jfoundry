package io.huahill.jfoundry.infrastructure.outbox.quarkus.externalization;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import io.huahill.jfoundry.application.event.externalization.AggregateRoutingResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizer;
import io.huahill.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import io.huahill.jfoundry.application.event.outbox.DefaultDomainEventOutboxRecorder;
import io.huahill.jfoundry.application.event.outbox.DomainEventOutboxRecorder;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.application.messaging.PayloadSerializer;

/// Produces the replaceable defaults used by Quarkus Outbox externalization.
@ApplicationScoped
public final class QuarkusOutboxExternalizationProducer {

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
    DomainEventOutboxRecorder domainEventOutboxRecorder(
            Instance<OutboxMessageStore> outboxMessageStore,
            PayloadSerializer payloadSerializer,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver,
            @Any Instance<DomainEventExternalizer<?>> externalizers) {
        return new DefaultDomainEventOutboxRecorder(
                () -> require(outboxMessageStore, "Automatic domain-event externalization requires an OutboxMessageStore CDI bean"),
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
