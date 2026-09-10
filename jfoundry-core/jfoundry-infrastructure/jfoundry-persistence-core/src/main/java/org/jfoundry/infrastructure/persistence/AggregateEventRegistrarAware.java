package org.jfoundry.infrastructure.persistence;

/// Receives the runtime-managed event registrar used by a repository adapter.
public interface AggregateEventRegistrarAware {

    /// Injects the registrar selected by the runtime integration.
    void setAggregateEventRegistrar(AggregateEventRegistrar registrar);
}
