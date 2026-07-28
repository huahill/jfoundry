/**
 * Application-layer domain event orchestration contracts.
 *
 * <p>This package defines the application-ring runtime boundary for domain event dispatch:
 * application services register touched aggregates in a scoped context, and runtime adapters
 * later drain and dispatch the resulting domain events. These types intentionally depend on
 * domain abstractions such as {@code EventRecordable} and {@code DomainEvent}, while leaving
 * threading, transactions, and transport concerns to outer adapters.
 */
@org.jmolecules.architecture.onion.simplified.ApplicationRing
package org.jfoundry.application.event;
