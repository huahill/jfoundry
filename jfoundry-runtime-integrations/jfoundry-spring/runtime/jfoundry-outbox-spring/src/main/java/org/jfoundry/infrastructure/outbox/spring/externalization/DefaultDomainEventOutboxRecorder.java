package org.jfoundry.infrastructure.outbox.spring.externalization;

import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.event.externalization.AggregateRoutingMetadata;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.ExternalizedEvent;
import org.jfoundry.application.event.externalization.ExternalizationRule;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;
import java.util.List;

/// Default transactional Outbox recorder for externalized domain events.
///
/// This component persists events matching externalization rules as broker-neutral
/// {@link OutboxMessage} records in the current transaction. It does not publish Spring
/// application events or dispatch messages to brokers.
public class DefaultDomainEventOutboxRecorder implements DomainEventOutboxRecorder {

    private final OutboxMessageStore outboxRepository;
    private final PayloadSerializer payloadSerializer;
    private final ExternalizationRuleResolver ruleResolver;
    private final AggregateRoutingResolver aggregateRoutingResolver;
    private final DomainEventExternalizationResolver externalizationResolver;

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxRepository,
                                          PayloadSerializer payloadSerializer,
                                          ExternalizationRuleResolver ruleResolver) {
        this(outboxRepository, payloadSerializer, ruleResolver, new AggregateRoutingResolver(),
                new DomainEventExternalizationResolver(List.of()));
    }

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxRepository,
                                          PayloadSerializer payloadSerializer,
                                          ExternalizationRuleResolver ruleResolver,
                                          AggregateRoutingResolver aggregateRoutingResolver) {
        this(outboxRepository, payloadSerializer, ruleResolver, aggregateRoutingResolver,
                new DomainEventExternalizationResolver(List.of()));
    }

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxRepository,
                                          PayloadSerializer payloadSerializer,
                                          ExternalizationRuleResolver ruleResolver,
                                          AggregateRoutingResolver aggregateRoutingResolver,
                                          DomainEventExternalizationResolver externalizationResolver) {
        this.outboxRepository = outboxRepository;
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
        for (DomainEvent event : events) {
            record(event);
        }
    }

    private void record(DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null.");
        }
        List<ExternalizedEvent> mappedEvents = externalizationResolver.resolve(event).orElse(null);
        if (mappedEvents != null) {
            mappedEvents.forEach(mappedEvent -> recordMapped(event, mappedEvent));
            return;
        }
        ExternalizationRule rule = ruleResolver.resolve(event).orElse(null);
        if (rule == null) {
            return;
        }
        String eventId = resolveEventId(event);
        String payloadType = event.getClass().getName();
        String payloadJson = payloadSerializer.serialize(event);
        Instant occurredAt = resolveOccurredAt(event);
        AggregateRoutingMetadata aggregate = aggregateRoutingResolver.resolve(event).orElse(null);
        String payloadKey = rule.payloadKey();
        if (payloadKey == null && aggregate != null) {
            payloadKey = aggregate.aggregateId();
        }
        OutboxMessage entry = OutboxMessage.newPending(
                eventId,
                rule.topic(),
                payloadKey,
                payloadType,
                payloadJson,
                occurredAt,
                aggregate != null ? aggregate.aggregateType() : null,
                aggregate != null ? aggregate.aggregateId() : null,
                aggregate != null ? aggregate.aggregateVersion() : null);
        outboxRepository.append(entry);
    }

    private void recordMapped(DomainEvent sourceEvent, ExternalizedEvent mappedEvent) {
        outboxRepository.append(OutboxMessage.newPending(
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
