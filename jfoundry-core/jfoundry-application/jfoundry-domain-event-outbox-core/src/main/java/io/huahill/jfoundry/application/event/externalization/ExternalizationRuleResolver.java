package io.huahill.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;
import org.jmolecules.event.annotation.Externalized;
import org.jmolecules.event.types.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
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
        return Optional.of(new ExternalizationRule(Objects.requireNonNull(metadata.topic())));
    }

    private ResolvedMetadata computeMetadata(Class<?> eventType) {
        @Nullable MessageRouting routing = eventType.getAnnotation(MessageRouting.class);
        @Nullable Externalized externalized = eventType.getAnnotation(Externalized.class);
        boolean hasExternalized = externalized != null;
        boolean hasRouting = routing != null;

        if (!hasExternalized) {
            return new ResolvedMetadata(false, hasRouting, null);
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
        return new ResolvedMetadata(true, false, topic);
    }

    private record ResolvedMetadata(boolean externalized, boolean routingOnly, @Nullable String topic) {
    }
}
