package org.jfoundry.application.event.externalization;

import org.jmolecules.event.annotation.Externalized;
import org.jmolecules.event.types.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Resolves externalization rules for domain events:
/// <ol>
///   <li>{@link MessageRouting} on the event class wins and provides {@code topic()}.</li>
///   <li>Without {@code @MessageRouting}, non-empty {@link Externalized#value()} provides the topic.</li>
///   <li>Without {@code @MessageRouting}, an empty {@code @Externalized.value()} fails fast.</li>
///   <li>Without {@code @Externalized}, this resolver returns an empty {@code Optional}.</li>
/// </ol>
public class ExternalizationRuleResolver {

    private static final Logger log = LoggerFactory.getLogger(ExternalizationRuleResolver.class);

    private final Map<Class<?>, ResolvedMetadata> cache = new ConcurrentHashMap<>();

    public Optional<ExternalizationRule> resolve(DomainEvent event) {
        Class<?> eventType = event.getClass();
        ResolvedMetadata metadata = cache.computeIfAbsent(eventType, this::computeMetadata);
        if (!metadata.externalized()) {
            if (metadata.routingOnly()) {
                log.warn("Class {} is annotated with @MessageRouting but not @Externalized; the event will not be externalized. "
                        + "Add @Externalized as well when externalization is required.", eventType.getName());
            }
            return Optional.empty();
        }
        String payloadKey = evaluateKey(event, metadata.keyPath());
        return Optional.of(new ExternalizationRule(metadata.topic(), payloadKey));
    }

    private ResolvedMetadata computeMetadata(Class<?> eventType) {
        MessageRouting routing = eventType.getAnnotation(MessageRouting.class);
        Externalized externalized = eventType.getAnnotation(Externalized.class);
        boolean hasExternalized = externalized != null;
        boolean hasRouting = routing != null;

        if (!hasExternalized) {
            return new ResolvedMetadata(false, hasRouting, null, null);
        }

        String topic;
        if (hasRouting) {
            topic = routing.topic();
        } else {
            String externalizedValue = externalized.value();
            if (externalizedValue == null || externalizedValue.isEmpty()) {
                throw new IllegalStateException(
                        "Event class " + eventType.getName() + " is annotated with @Externalized but does not specify a topic. "
                                + "Use @MessageRouting(topic = ...) or @Externalized(\"<topic>\") to specify one explicitly.");
            }
            topic = externalizedValue;
        }

        String keyPath = null;
        if (hasRouting && !routing.key().isEmpty()) {
            keyPath = PropertyPathReader.normalize(routing.key());
        }
        return new ResolvedMetadata(true, false, topic, keyPath);
    }

    private String evaluateKey(DomainEvent event, String keyPath) {
        if (keyPath == null) {
            return null;
        }
        try {
            Object value = PropertyPathReader.read(event, keyPath);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            log.warn("Failed to resolve @MessageRouting.key property path for event {}; payloadKey falls back to null. Cause: {}",
                    event.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private record ResolvedMetadata(boolean externalized, boolean routingOnly,
                                     String topic, String keyPath) {
    }
}
