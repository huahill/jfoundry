package org.jfoundry.infrastructure.inbox.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.inbox.InboxTemplate;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategies;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;

import javax.sql.DataSource;
import java.sql.SQLException;

/// Provides JPA-backed Inbox components for Helidon applications.
@Dependent
public final class HelidonJpaInboxProducer {

    @Produces
    JpaInboxClaimStrategy claimStrategy(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return claimStrategyForProductName(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine database product for JpaInboxClaimStrategy", exception);
        }
    }

    JpaInboxClaimStrategy claimStrategyForProductName(String productName) {
        return JpaInboxClaimStrategies.forProductName(productName);
    }

    @Produces
    InboxMessageStore inboxMessageStore(EntityManager entityManager, JpaInboxClaimStrategy claimStrategy) {
        return new JpaInboxMessageStore(entityManager, claimStrategy);
    }

    @Produces
    InboxTemplate inboxTemplate(InboxMessageStore store, TransactionRunner transactionRunner) {
        return new InboxTemplate(store, transactionRunner);
    }
}
