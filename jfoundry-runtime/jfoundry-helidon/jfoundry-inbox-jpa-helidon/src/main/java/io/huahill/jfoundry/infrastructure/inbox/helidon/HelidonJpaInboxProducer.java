package io.huahill.jfoundry.infrastructure.inbox.helidon;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.persistence.EntityManager;
import io.huahill.jfoundry.application.inbox.InboxMessageStore;
import io.huahill.jfoundry.application.inbox.InboxTemplate;
import io.huahill.jfoundry.application.transaction.TransactionRunner;
import io.huahill.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategies;
import io.huahill.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import io.huahill.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;

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
