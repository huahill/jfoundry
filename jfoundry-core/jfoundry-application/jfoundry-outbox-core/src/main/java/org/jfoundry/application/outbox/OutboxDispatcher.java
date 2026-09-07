package org.jfoundry.application.outbox;

/// Outbox dispatch service port. Implementations perform dispatch runs by claiming and delivering
/// entries by batch size. Runtime triggers such as schedulers invoke this port but are not its
/// implementations.
public interface OutboxDispatcher {

    /// @param batchSize maximum number of entries claimed by one dispatch run
    void dispatch(int batchSize);
}
