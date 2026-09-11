/**
 * Spring adapter that publishes domain events into the Outbox recorder.
 *
 * <p>Types in this package dispatch already-resolved domain events to
 * {@link org.jfoundry.application.outbox.DomainEventOutboxRecorder} so matching
 * events are persisted as broker-neutral Outbox records inside the current
 * transaction.
 */
@org.jmolecules.architecture.onion.simplified.InfrastructureRing
package org.jfoundry.infrastructure.outbox.spring.externalization;

