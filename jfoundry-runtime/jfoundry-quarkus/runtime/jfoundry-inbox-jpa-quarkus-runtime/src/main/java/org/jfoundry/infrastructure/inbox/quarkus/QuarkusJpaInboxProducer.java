package org.jfoundry.infrastructure.inbox.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
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

/// Provides default JPA Inbox components for Quarkus applications.
@ApplicationScoped
public final class QuarkusJpaInboxProducer {

    @Produces
    @DefaultBean
    JpaInboxClaimStrategy claimStrategy(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return JpaInboxClaimStrategies.forProductName(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine database product for JpaInboxClaimStrategy", exception);
        }
    }

    @Produces
    @DefaultBean
    InboxMessageStore inboxMessageStore(EntityManager entityManager, JpaInboxClaimStrategy claimStrategy) {
        return new JpaInboxMessageStore(entityManager, claimStrategy);
    }

    @Produces
    @DefaultBean
    InboxTemplate inboxTemplate(InboxMessageStore store, TransactionRunner transactionRunner) {
        return new InboxTemplate(store, transactionRunner);
    }
}
