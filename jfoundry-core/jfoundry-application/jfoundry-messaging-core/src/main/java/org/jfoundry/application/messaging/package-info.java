/**
 * Application-layer messaging SPI.
 *
 * <p>This package defines framework-neutral outbound messaging contracts such as
 * {@code MessageSender} and {@code PayloadSerializer}. It belongs to the application core
 * because it expresses how jfoundry application services hand off messages to external systems
 * without committing to Spring, Kafka, Jackson, or any specific broker/runtime adapter.
 *
 * <p>Application-service boundary control and domain-event registration/dispatch contracts now
 * live in {@code org.jfoundry.application.event}. This package stays focused on generic messaging
 * concerns: translating already-selected payloads into broker-neutral send requests that outer
 * adapters can serialize and deliver.
 */
@org.jmolecules.architecture.onion.simplified.ApplicationRing
@org.jspecify.annotations.NullMarked
package org.jfoundry.application.messaging;
