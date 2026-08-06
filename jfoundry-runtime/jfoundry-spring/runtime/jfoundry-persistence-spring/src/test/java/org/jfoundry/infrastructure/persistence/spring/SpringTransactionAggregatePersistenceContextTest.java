package org.jfoundry.infrastructure.persistence.spring;

import org.jfoundry.infrastructure.persistence.PersistenceStateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringTransactionAggregatePersistenceContextTest {

    private static final PersistenceStateKey<Long> VERSION =
            PersistenceStateKey.of("test-version", Long.class);

    private SpringTransactionAggregatePersistenceContext context;
    private TransactionTemplate required;
    private TransactionTemplate requiresNew;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:persistence-context;DB_CLOSE_DELAY=-1", "sa", "");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        context = new SpringTransactionAggregatePersistenceContext();
        required = new TransactionTemplate(transactionManager);
        requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    void failsFastWithoutAnActiveTransaction() {
        assertThatThrownBy(() -> context.attach(new Object(), VERSION, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    @Test
    void requiredParticipationSharesTrackedState() {
        Object aggregate = new Object();

        required.executeWithoutResult(status -> {
            context.attach(aggregate, VERSION, 1L);
            required.executeWithoutResult(inner ->
                    assertThat(context.require(aggregate, VERSION)).isEqualTo(1L));
        });
    }

    @Test
    void completedTransactionDoesNotLeakTrackedState() {
        Object aggregate = new Object();
        required.executeWithoutResult(status -> context.attach(aggregate, VERSION, 1L));

        required.executeWithoutResult(status ->
                assertThatThrownBy(() -> context.require(aggregate, VERSION))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("not tracked"));
    }

    @Test
    void rolledBackTransactionDoesNotLeakTrackedState() {
        Object aggregate = new Object();
        required.executeWithoutResult(status -> {
            context.attach(aggregate, VERSION, 1L);
            status.setRollbackOnly();
        });

        required.executeWithoutResult(status ->
                assertThatThrownBy(() -> context.require(aggregate, VERSION))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("not tracked"));
    }

    @Test
    void requiresNewUsesIndependentStateAndResumesOuterState() {
        Object aggregate = new Object();

        required.executeWithoutResult(status -> {
            context.attach(aggregate, VERSION, 1L);

            requiresNew.executeWithoutResult(inner -> {
                assertThatThrownBy(() -> context.require(aggregate, VERSION))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("not tracked");
                context.attach(aggregate, VERSION, 2L);
                assertThat(context.require(aggregate, VERSION)).isEqualTo(2L);
            });

            assertThat(context.require(aggregate, VERSION)).isEqualTo(1L);
        });
    }

    @Test
    void enforcesAttachAndReplaceContract() {
        Object aggregate = new Object();

        required.executeWithoutResult(status -> {
            context.attach(aggregate, VERSION, 1L);
            assertThatThrownBy(() -> context.attach(aggregate, VERSION, 2L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already attached");

            context.replace(aggregate, VERSION, 3L);
            assertThat(context.require(aggregate, VERSION)).isEqualTo(3L);
        });
    }
}
