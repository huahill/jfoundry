/**
 * Application-layer domain-event externalization rules and contracts.
 *
 * <p>This package defines the framework-neutral policy used to decide whether a domain event
 * should be externalized and how its topic and routing metadata are resolved. Types here intentionally depend on domain-event abstractions from
 * {@code jfoundry-domain}: this is application-layer orchestration around domain events, not a
 * generic broker API.
 *
 * <p>Infrastructure adapters such as Spring event dispatchers, Outbox recorders, Kafka senders, or
 * Jackson serializers consume these contracts; they do not redefine the externalization rules.
 */
@org.jmolecules.architecture.onion.simplified.ApplicationRing
package org.jfoundry.application.event.externalization;
