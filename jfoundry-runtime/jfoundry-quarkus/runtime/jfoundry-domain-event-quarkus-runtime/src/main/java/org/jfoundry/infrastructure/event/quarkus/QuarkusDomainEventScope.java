package org.jfoundry.infrastructure.event.quarkus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.jfoundry.domain.event.EventRecordable;
import org.jfoundry.infrastructure.event.jta.JtaDomainEventScope;

/// Quarkus CDI registration for the shared JTA domain-event scope.
@ApplicationScoped
public class QuarkusDomainEventScope {

    private final JtaDomainEventScope delegate;

    @Inject
    public QuarkusDomainEventScope(TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.delegate = new JtaDomainEventScope(transactionSynchronizationRegistry);
    }

    void register(EventRecordable aggregate) {
        this.delegate.register(aggregate);
    }

    JtaDomainEventScope delegate() {
        return this.delegate;
    }
}
