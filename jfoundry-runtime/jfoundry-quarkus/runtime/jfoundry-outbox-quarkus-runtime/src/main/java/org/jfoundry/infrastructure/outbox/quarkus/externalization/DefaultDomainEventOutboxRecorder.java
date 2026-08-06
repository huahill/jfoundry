package org.jfoundry.infrastructure.outbox.quarkus.externalization;

import jakarta.enterprise.inject.Instance;
import org.jfoundry.application.event.externalization.AggregateRoutingMetadata;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.ExternalizedEvent;
import org.jfoundry.application.event.externalization.ExternalizationRule;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;
import java.util.List;

/// Records explicitly externalized domain events in the current Quarkus transaction.
public final class DefaultDomainEventOutboxRecorder implements DomainEventOutboxRecorder {

    private final Instance<OutboxMessageStore> outboxMessageStore;
    private final PayloadSerializer payloadSerializer;
    private final ExternalizationRuleResolver ruleResolver;
    private final AggregateRoutingResolver aggregateRoutingResolver;
    private final DomainEventExternalizationResolver externalizationResolver;

    public DefaultDomainEventOutboxRecorder(
            Instance<OutboxMessageStore> outboxMessageStore,
            PayloadSerializer payloadSerializer,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver) {
        this(outboxMessageStore, payloadSerializer, ruleResolver, aggregateRoutingResolver,
                new DomainEventExternalizationResolver(List.of()));
    }

    public DefaultDomainEventOutboxRecorder(
            Instance<OutboxMessageStore> outboxMessageStore,
            PayloadSerializer payloadSerializer,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver,
            DomainEventExternalizationResolver externalizationResolver) {
        this.outboxMessageStore = outboxMessageStore;
        this.payloadSerializer = payloadSerializer;
        this.ruleResolver = ruleResolver;
        this.aggregateRoutingResolver = aggregateRoutingResolver;
        this.externalizationResolver = externalizationResolver;
    }

    @Override
    public void record(List<? extends DomainEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Domain events must not be null.");
        }
        OutboxMessageStore store = null;
        for (DomainEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("Domain event must not be null.");
            }
            List<ExternalizedEvent> mappedEvents = externalizationResolver.resolve(event).orElse(null);
            if (mappedEvents != null) {
                if (store == null && !mappedEvents.isEmpty()) {
                    store = requireOutboxMessageStore();
                }
                for (ExternalizedEvent mappedEvent : mappedEvents) {
                    recordMapped(store, event, mappedEvent);
                }
                continue;
            }
            ExternalizationRule rule = ruleResolver.resolve(event).orElse(null);
            if (rule == null) {
                continue;
            }
            if (store == null) {
                store = requireOutboxMessageStore();
            }
            record(store, event, rule);
        }
    }

    private void record(OutboxMessageStore store, DomainEvent event, ExternalizationRule rule) {
        AggregateRoutingMetadata aggregate = aggregateRoutingResolver.resolve(event).orElse(null);
        String payloadKey = rule.payloadKey();
        if (payloadKey == null && aggregate != null) {
            payloadKey = aggregate.aggregateId();
        }
        store.append(OutboxMessage.newPending(
                resolveEventId(event),
                rule.topic(),
                payloadKey,
                event.getClass().getName(),
                payloadSerializer.serialize(event),
                resolveOccurredAt(event),
                aggregate != null ? aggregate.aggregateType() : null,
                aggregate != null ? aggregate.aggregateId() : null,
                aggregate != null ? aggregate.aggregateVersion() : null));
    }

    private void recordMapped(OutboxMessageStore store, DomainEvent sourceEvent, ExternalizedEvent mappedEvent) {
        store.append(OutboxMessage.newPending(
                resolveEventId(sourceEvent),
                mappedEvent.topic(),
                mappedEvent.payloadKey(),
                mappedEvent.payloadType(),
                payloadSerializer.serialize(mappedEvent.payload()),
                resolveOccurredAt(sourceEvent),
                mappedEvent.aggregateType(),
                mappedEvent.aggregateId(),
                mappedEvent.aggregateVersion()));
    }

    private OutboxMessageStore requireOutboxMessageStore() {
        if (!outboxMessageStore.isResolvable()) {
            throw new IllegalStateException(
                    "Automatic domain-event externalization requires an OutboxMessageStore CDI bean");
        }
        return outboxMessageStore.get();
    }

    private static String resolveEventId(DomainEvent event) {
        if (event instanceof BaseDomainEvent baseEvent) {
            return baseEvent.getEventId().toString();
        }
        return java.util.UUID.randomUUID().toString();
    }

    private static Instant resolveOccurredAt(DomainEvent event) {
        if (event instanceof BaseDomainEvent baseEvent) {
            return baseEvent.getOccurredAt();
        }
        return Instant.now();
    }
}
