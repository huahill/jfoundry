package org.jfoundry.application.event.outbox;

import org.jfoundry.application.event.externalization.AggregateRoutingMetadata;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import org.jfoundry.application.event.externalization.ExternalizedEvent;
import org.jfoundry.application.event.externalization.ExternalizationRule;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.domain.event.BaseDomainEvent;
import org.jmolecules.event.types.DomainEvent;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/// Default transactional Outbox recorder for externalized domain events.
///
/// This component persists matching events as broker-neutral {@link OutboxMessage}
/// records in the current transaction. Mapped destinations receive their own Outbox
/// event ids; {@code @Externalized} rows store the destination topic as
/// {@code payloadType}. It does not dispatch messages to brokers.
public final class DefaultDomainEventOutboxRecorder implements DomainEventOutboxRecorder {

    private final Supplier<OutboxMessageStore> outboxMessageStore;
    private final Supplier<PayloadSerializer> payloadSerializer;
    private final ExternalizationRuleResolver ruleResolver;
    private final AggregateRoutingResolver aggregateRoutingResolver;
    private final DomainEventExternalizationResolver externalizationResolver;

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxMessageStore,
                                            PayloadSerializer payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver) {
        this(outboxMessageStore, payloadSerializer, ruleResolver, new AggregateRoutingResolver());
    }

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxMessageStore,
                                            PayloadSerializer payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver,
                                            AggregateRoutingResolver aggregateRoutingResolver) {
        this(outboxMessageStore, payloadSerializer, ruleResolver, aggregateRoutingResolver,
                new DomainEventExternalizationResolver(List.of()));
    }

    public DefaultDomainEventOutboxRecorder(OutboxMessageStore outboxMessageStore,
                                            PayloadSerializer payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver,
                                            AggregateRoutingResolver aggregateRoutingResolver,
                                            DomainEventExternalizationResolver externalizationResolver) {
        this(asSupplier(outboxMessageStore, "outboxMessageStore must not be null"),
                asSupplier(payloadSerializer, "payloadSerializer must not be null"),
                ruleResolver,
                aggregateRoutingResolver,
                externalizationResolver);
    }

    public DefaultDomainEventOutboxRecorder(Supplier<OutboxMessageStore> outboxMessageStore,
                                            PayloadSerializer payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver,
                                            AggregateRoutingResolver aggregateRoutingResolver,
                                            DomainEventExternalizationResolver externalizationResolver) {
        this(outboxMessageStore,
                asSupplier(payloadSerializer, "payloadSerializer must not be null"),
                ruleResolver,
                aggregateRoutingResolver,
                externalizationResolver);
    }

    public DefaultDomainEventOutboxRecorder(Supplier<OutboxMessageStore> outboxMessageStore,
                                            Supplier<PayloadSerializer> payloadSerializer,
                                            ExternalizationRuleResolver ruleResolver,
                                            AggregateRoutingResolver aggregateRoutingResolver,
                                            DomainEventExternalizationResolver externalizationResolver) {
        this.outboxMessageStore = Objects.requireNonNull(outboxMessageStore, "outboxMessageStore must not be null");
        this.payloadSerializer = Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");
        this.ruleResolver = Objects.requireNonNull(ruleResolver, "ruleResolver must not be null");
        this.aggregateRoutingResolver = Objects.requireNonNull(aggregateRoutingResolver,
                "aggregateRoutingResolver must not be null");
        this.externalizationResolver = Objects.requireNonNull(externalizationResolver,
                "externalizationResolver must not be null");
    }

    @Override
    public void record(List<? extends DomainEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("Domain events must not be null.");
        }
        RecordingSession session = new RecordingSession();
        for (DomainEvent event : events) {
            record(session, event);
        }
    }

    private void record(RecordingSession session, DomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Domain event must not be null.");
        }
        List<ExternalizedEvent> mappedEvents = externalizationResolver.resolve(event).orElse(null);
        if (mappedEvents != null) {
            recordMapped(session, event, mappedEvents);
            return;
        }
        ExternalizationRule rule = ruleResolver.resolve(event).orElse(null);
        if (rule == null) {
            return;
        }
        AggregateRoutingMetadata aggregate = aggregateRoutingResolver.resolve(event).orElse(null);
        String payloadKey = rule.payloadKey();
        if (payloadKey == null && aggregate != null) {
            payloadKey = aggregate.aggregateId();
        }
        session.store().append(OutboxMessage.newPending(
                resolveEventId(event),
                rule.topic(),
                payloadKey,
                rule.topic(),
                session.serializer().serialize(event),
                resolveOccurredAt(event),
                aggregate != null ? aggregate.aggregateType() : null,
                aggregate != null ? aggregate.aggregateId() : null,
                aggregate != null ? aggregate.aggregateVersion() : null));
    }

    private void recordMapped(RecordingSession session, DomainEvent sourceEvent,
                              List<ExternalizedEvent> mappedEvents) {
        if (mappedEvents.isEmpty()) {
            return;
        }
        Set<Destination> destinations = new HashSet<>();
        for (ExternalizedEvent mappedEvent : mappedEvents) {
            Destination destination = new Destination(mappedEvent.payloadType(), mappedEvent.topic());
            if (!destinations.add(destination)) {
                throw new IllegalStateException(
                        "Mapped Outbox destinations must be unique per source event: payloadType="
                                + mappedEvent.payloadType() + ", topic=" + mappedEvent.topic());
            }
        }
        String sourceEventId = resolveEventId(sourceEvent);
        Instant occurredAt = resolveOccurredAt(sourceEvent);
        for (ExternalizedEvent mappedEvent : mappedEvents) {
            session.store().append(OutboxMessage.newPending(
                    MappedOutboxEventIds.derive(sourceEventId, mappedEvent.payloadType(), mappedEvent.topic()),
                    mappedEvent.topic(),
                    mappedEvent.payloadKey(),
                    mappedEvent.payloadType(),
                    session.serializer().serialize(mappedEvent.payload()),
                    occurredAt,
                    mappedEvent.aggregateType(),
                    mappedEvent.aggregateId(),
                    mappedEvent.aggregateVersion()));
        }
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

    private static <T> Supplier<T> asSupplier(T value, String message) {
        Objects.requireNonNull(value, message);
        return () -> value;
    }

    private final class RecordingSession {
        private OutboxMessageStore store;
        private PayloadSerializer serializer;

        private OutboxMessageStore store() {
            if (store == null) {
                store = Objects.requireNonNull(outboxMessageStore.get(), "outboxMessageStore must not be null");
            }
            return store;
        }

        private PayloadSerializer serializer() {
            if (serializer == null) {
                serializer = Objects.requireNonNull(payloadSerializer.get(), "payloadSerializer must not be null");
            }
            return serializer;
        }
    }

    private record Destination(String payloadType, String topic) {
    }
}
