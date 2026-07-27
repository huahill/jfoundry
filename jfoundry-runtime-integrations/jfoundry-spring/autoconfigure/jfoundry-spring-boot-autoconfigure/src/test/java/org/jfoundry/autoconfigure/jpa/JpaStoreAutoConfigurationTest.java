package org.jfoundry.autoconfigure.jpa;

import jakarta.persistence.EntityManagerFactory;
import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.application.inbox.InboxTemplate;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.transaction.TransactionRunner;
import org.jfoundry.autoconfigure.inbox.InboxAutoConfiguration;
import org.jfoundry.autoconfigure.inbox.InboxJpaAutoConfiguration;
import org.jfoundry.autoconfigure.inbox.InboxMybatisPlusAutoConfiguration;
import org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration;
import org.jfoundry.autoconfigure.outbox.persistence.OutboxJpaAutoConfiguration;
import org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.jfoundry.infrastructure.inbox.jpa.PostgreSqlJpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.MybatisPlusOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaStoreAutoConfigurationTest {

    private final ApplicationContextRunner outboxRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OutboxJpaAutoConfiguration.class,
                    OutboxDispatcherAutoConfiguration.class));

    private final ApplicationContextRunner inboxRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    InboxAutoConfiguration.class,
                    InboxJpaAutoConfiguration.class));

    @Test
    void exposesJpaStoreAutoConfigurations() {
        assertThatCode(() -> {
            Class.forName("org.jfoundry.autoconfigure.outbox.persistence.OutboxJpaAutoConfiguration");
            Class.forName("org.jfoundry.autoconfigure.inbox.InboxJpaAutoConfiguration");
        }).doesNotThrowAnyException();
    }

    @Test
    void createsJpaOutboxStoreBeforeTheDispatcherConfiguration() {
        outboxRunner
                .withPropertyValues("jfoundry.outbox.dispatcher.mode=none")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(MessageSender.class, () -> outbound -> SendResult.ok())
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxMessageStore.class);
                    assertThat(context.getBean(OutboxMessageStore.class))
                            .isInstanceOf(JpaOutboxMessageStore.class);
                    assertThat(context).doesNotHaveBean(OutboxDispatcher.class);
                });
    }

    @Test
    void backsOffWhenSharedEntityManagerCreatorIsMissing() {
        JpaInboxClaimStrategy userStrategy = (entityManager, messageId, consumerName, claimToken, now) -> false;

        outboxRunner
                .withClassLoader(new FilteredClassLoader(SharedEntityManagerCreator.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).doesNotHaveBean(OutboxMessageStore.class));

        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(JpaInboxClaimStrategy.class, () -> userStrategy)
                .run(context -> assertThat(context).hasSingleBean(InboxMessageStore.class)
                        .getBean(InboxMessageStore.class).isInstanceOf(JpaInboxMessageStore.class));

        inboxRunner
                .withClassLoader(new FilteredClassLoader(SharedEntityManagerCreator.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(JpaInboxClaimStrategy.class, () -> userStrategy)
                .run(context -> assertThat(context).doesNotHaveBean(InboxMessageStore.class));
    }

    @Test
    void backsOffFromJpaOutboxStoreWhenUserProvidesOne() {
        OutboxMessageStore userStore = mock(OutboxMessageStore.class);

        outboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(OutboxMessageStore.class, () -> userStore)
                .run(context -> assertThat(context).hasSingleBean(OutboxMessageStore.class)
                        .getBean(OutboxMessageStore.class).isSameAs(userStore));
    }

    @Test
    void prefersMybatisPlusOutboxStoreWhenBothAdaptersAreAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        OutboxJpaAutoConfiguration.class,
                        OutboxMybatisPlusAutoConfiguration.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(OutboxMapper.class, () -> mock(OutboxMapper.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxMessageStore.class);
                    assertThat(context.getBean(OutboxMessageStore.class))
                            .isInstanceOf(MybatisPlusOutboxMessageStore.class);
                });
    }

    @Test
    void createsJpaInboxStoreAndTemplateUsingTheDatabaseProductStrategy() {
        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(DataSource.class, () -> dataSourceWithProduct("PostgreSQL 16"))
                .withBean(TransactionRunner.class, () -> mock(TransactionRunner.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaInboxClaimStrategy.class);
                    assertThat(context.getBean(JpaInboxClaimStrategy.class))
                            .isInstanceOf(PostgreSqlJpaInboxClaimStrategy.class);
                    assertThat(context).hasSingleBean(InboxMessageStore.class);
                    assertThat(context.getBean(InboxMessageStore.class))
                            .isInstanceOf(JpaInboxMessageStore.class);
                    assertThat(context).hasSingleBean(InboxTemplate.class);
                });
    }

    @Test
    void closesMetadataConnectionAfterCreatingTheDefaultInboxClaimStrategy() {
        MetadataDataSource metadataDataSource = metadataDataSourceWithProduct("PostgreSQL 16");

        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(DataSource.class, metadataDataSource::dataSource)
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaInboxClaimStrategy.class);
                    assertThat(context.getBean(JpaInboxClaimStrategy.class))
                            .isInstanceOf(PostgreSqlJpaInboxClaimStrategy.class);
                    verifyConnectionClosed(metadataDataSource.connection());
                });
    }

    @Test
    void prefersMybatisPlusInboxStoreWhenBothAdaptersAreAvailable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        InboxJpaAutoConfiguration.class,
                        InboxMybatisPlusAutoConfiguration.class,
                        InboxAutoConfiguration.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(DataSource.class, () -> dataSourceWithProduct("PostgreSQL 16"))
                .withBean(SqlSessionFactory.class, () -> mock(SqlSessionFactory.class))
                .withBean(InboxMessageMapper.class, () -> mock(InboxMessageMapper.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(InboxMessageStore.class);
                    assertThat(context.getBean(InboxMessageStore.class))
                            .isInstanceOf(MybatisPlusInboxMessageStore.class);
                });
    }

    @Test
    void backsOffFromJpaInboxClaimStrategyWhenUserProvidesOne() {
        JpaInboxClaimStrategy userStrategy = (entityManager, messageId, consumerName, claimToken, now) -> false;

        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(JpaInboxClaimStrategy.class, () -> userStrategy)
                .run(context -> {
                    assertThat(context).hasSingleBean(JpaInboxClaimStrategy.class)
                            .getBean(JpaInboxClaimStrategy.class).isSameAs(userStrategy);
                    assertThat(context.getBean(InboxMessageStore.class))
                            .isInstanceOf(JpaInboxMessageStore.class);
                });
    }

    @Test
    void backsOffFromJpaInboxStoreWithoutRequiringADataSource() {
        InboxMessageStore userStore = mock(InboxMessageStore.class);

        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(InboxMessageStore.class, () -> userStore)
                .run(context -> assertThat(context).hasSingleBean(InboxMessageStore.class)
                        .getBean(InboxMessageStore.class).isSameAs(userStore));
    }

    @Test
    void failsClearlyForUnsupportedInboxDatabaseProduct() {
        inboxRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withBean(DataSource.class, () -> dataSourceWithProduct("H2"))
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseMessage(
                                "Unsupported database product: H2. Provide a JpaInboxClaimStrategy for this database."));
    }

    private static DataSource dataSourceWithProduct(String productName) {
        return metadataDataSourceWithProduct(productName).dataSource();
    }

    private static MetadataDataSource metadataDataSourceWithProduct(String productName) {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        try {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn(productName);
        } catch (SQLException exception) {
            throw new AssertionError("Unable to configure database metadata mock", exception);
        }
        return new MetadataDataSource(dataSource, connection);
    }

    private static void verifyConnectionClosed(Connection connection) {
        try {
            verify(connection).close();
        } catch (SQLException exception) {
            throw new AssertionError("Unable to verify metadata connection closure", exception);
        }
    }

    private record MetadataDataSource(DataSource dataSource, Connection connection) {
    }
}
