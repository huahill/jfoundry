package org.jfoundry.application.event.externalization;

import org.jspecify.annotations.Nullable;

/// Resolved externalization rule.
/// @param topic target topic
/// @param payloadKey routing key, possibly null
public record ExternalizationRule(String topic, @Nullable String payloadKey) {
}
