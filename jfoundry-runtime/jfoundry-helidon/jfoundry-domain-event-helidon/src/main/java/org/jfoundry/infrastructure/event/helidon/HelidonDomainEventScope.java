package org.jfoundry.infrastructure.event.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.event.jta.JtaDomainEventScope;

/// Helidon MP CDI registration for the shared JTA domain-event scope.
@Dependent
public class HelidonDomainEventScope {

    private final JtaDomainEventScope delegate;

    @Inject
    public HelidonDomainEventScope(TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.delegate = new JtaDomainEventScope(transactionSynchronizationRegistry);
    }

    void register(EventRecordable aggregate) {
        this.delegate.register(aggregate);
    }

    JtaDomainEventScope delegate() {
        return this.delegate;
    }
}
