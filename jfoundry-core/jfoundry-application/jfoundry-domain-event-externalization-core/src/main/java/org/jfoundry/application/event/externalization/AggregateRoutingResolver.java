package org.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;
import org.jmolecules.event.types.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Resolves aggregate routing metadata from {@link AggregateRouting}.
public class AggregateRoutingResolver {

    private static final Logger log = LoggerFactory.getLogger(AggregateRoutingResolver.class);

    private final Map<Class<?>, ResolvedMetadata> cache = new ConcurrentHashMap<>();

    public Optional<AggregateRoutingMetadata> resolve(DomainEvent event) {
        Class<?> eventType = event.getClass();
        ResolvedMetadata metadata = cache.computeIfAbsent(eventType, this::computeMetadata);
        if (!metadata.annotated()) {
            return Optional.empty();
        }
        try {
            Object aggregateId = PropertyPathReader.read(event, Objects.requireNonNull(metadata.idPath()));
            if (aggregateId == null) {
                return Optional.empty();
            }
            @Nullable Long aggregateVersion = resolveVersion(event, metadata.versionPath());
            return Optional.of(new AggregateRoutingMetadata(
                    Objects.requireNonNull(metadata.aggregateType()), aggregateId.toString(), aggregateVersion));
        } catch (Exception e) {
            log.warn("Failed to resolve @AggregateRouting property path for event {}; aggregate metadata is empty. Cause: {}",
                    eventType.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    private ResolvedMetadata computeMetadata(Class<?> eventType) {
        @Nullable AggregateRouting routing = eventType.getAnnotation(AggregateRouting.class);
        if (routing == null) {
            return new ResolvedMetadata(false, null, null, null);
        }
        String aggregateType = routing.type().isEmpty() ? eventType.getSimpleName() : routing.type();
        @Nullable String versionPath = routing.version().isEmpty() ? null : PropertyPathReader.normalize(routing.version());
        return new ResolvedMetadata(
                true,
                aggregateType,
                PropertyPathReader.normalize(routing.id()),
                versionPath);
    }

    private @Nullable Long resolveVersion(DomainEvent event, @Nullable String versionPath)
            throws ReflectiveOperationException {
        if (versionPath == null) {
            return null;
        }
        @Nullable Object value = PropertyPathReader.read(event, versionPath);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private record ResolvedMetadata(boolean annotated, @Nullable String aggregateType,
                                    @Nullable String idPath, @Nullable String versionPath) {
    }
}
