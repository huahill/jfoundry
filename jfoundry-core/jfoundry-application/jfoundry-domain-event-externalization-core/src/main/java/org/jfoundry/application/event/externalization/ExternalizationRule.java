package org.jfoundry.application.event.externalization;

/// Resolved externalization rule.
/// @param topic target topic
/// @param payloadKey routing key, possibly null
public record ExternalizationRule(String topic, String payloadKey) {
}
