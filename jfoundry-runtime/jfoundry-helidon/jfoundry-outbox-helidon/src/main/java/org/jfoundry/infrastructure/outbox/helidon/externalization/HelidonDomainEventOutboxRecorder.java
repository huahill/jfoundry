package org.jfoundry.infrastructure.outbox.helidon.externalization;

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

/// Records explicitly externalized domain events in the current Helidon transaction.
public final class HelidonDomainEventOutboxRecorder implements DomainEventOutboxRecorder {

    private final Instance<OutboxMessageStore> outboxMessageStore;
    private final Instance<PayloadSerializer> payloadSerializer;
    private final ExternalizationRuleResolver ruleResolver;
    private final AggregateRoutingResolver aggregateRoutingResolver;
    private final DomainEventExternalizationResolver externalizationResolver;

    public HelidonDomainEventOutboxRecorder(Instance<OutboxMessageStore> outboxMessageStore,
                                            Instance<PayloadSerializer> payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver,
                                            AggregateRoutingResolver aggregateRoutingResolver) {
        this(outboxMessageStore, payloadSerializer, ruleResolver, aggregateRoutingResolver,
                new DomainEventExternalizationResolver(List.of()));
    }

    public HelidonDomainEventOutboxRecorder(Instance<OutboxMessageStore> outboxMessageStore,
                                            Instance<PayloadSerializer> payloadSerializer,
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
                    store = require(outboxMessageStore,
                            "Automatic domain-event externalization requires an OutboxMessageStore CDI bean");
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
                store = require(outboxMessageStore,
                        "Automatic domain-event externalization requires an OutboxMessageStore CDI bean");
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
                require(payloadSerializer,
                        "Automatic domain-event externalization requires a PayloadSerializer CDI bean").serialize(event),
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
                require(payloadSerializer,
                        "Automatic domain-event externalization requires a PayloadSerializer CDI bean")
                        .serialize(mappedEvent.payload()),
                resolveOccurredAt(sourceEvent),
                mappedEvent.aggregateType(),
                mappedEvent.aggregateId(),
                mappedEvent.aggregateVersion()));
    }

    private static <T> T require(Instance<T> instance, String message) {
        if (!instance.isResolvable()) {
            throw new IllegalStateException(message);
        }
        return instance.get();
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
