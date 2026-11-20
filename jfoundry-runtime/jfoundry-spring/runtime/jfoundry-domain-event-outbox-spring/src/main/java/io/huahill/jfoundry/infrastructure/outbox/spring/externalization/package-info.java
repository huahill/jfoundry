/// Spring adapter that publishes domain events into the Outbox recorder.
///
/// Types in this package dispatch already-resolved domain events to
/// [io.huahill.jfoundry.application.event.outbox.DomainEventOutboxRecorder] so matching
/// events are persisted as broker-neutral Outbox records inside the current
/// transaction.
@org.jmolecules.architecture.onion.simplified.InfrastructureRing
package io.huahill.jfoundry.infrastructure.outbox.spring.externalization;
