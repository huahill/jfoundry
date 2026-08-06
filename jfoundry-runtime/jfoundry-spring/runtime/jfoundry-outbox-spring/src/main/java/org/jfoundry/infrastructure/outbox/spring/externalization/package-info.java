/**
 * Spring adapter for domain-event externalization into the Outbox.
 *
 * <p>Types in this package implement the recorder that takes already-resolved domain-event
 * externalization rules and persists matching events as broker-neutral Outbox records inside the
 * current transaction.
 */
@org.jmolecules.architecture.onion.simplified.InfrastructureRing
package org.jfoundry.infrastructure.outbox.spring.externalization;
