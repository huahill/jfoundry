package org.jfoundry.infrastructure.inbox.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaInboxClaimStrategiesTest {

    @Test
    void selectsOnlyBuiltInProducts() {
        assertThat(JpaInboxClaimStrategies.forProductName("PostgreSQL 16.4"))
                .isInstanceOf(PostgreSqlJpaInboxClaimStrategy.class);
        assertThat(JpaInboxClaimStrategies.forProductName("MySQL"))
                .isInstanceOf(MySqlJpaInboxClaimStrategy.class);
    }

    @Test
    void rejectsUnsupportedProductWithOverrideInstruction() {
        assertThatThrownBy(() -> JpaInboxClaimStrategies.forProductName("H2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported database product: H2")
                .hasMessageContaining("JpaInboxClaimStrategy");
    }

    // H2 cannot execute PostgreSQL ON CONFLICT; runtime integration tests cover real PostgreSQL/MySQL.
    @Test
    void postgresUsesNoConflictInsertAndReturnsWhetherTheRowWasInserted() {
        RecordedNativeQuery recorded = new RecordedNativeQuery(1);
        Instant now = Instant.parse("2026-07-16T10:15:30Z");

        boolean claimed = new PostgreSqlJpaInboxClaimStrategy()
                .tryClaim(recorded.entityManager(), "msg-1", "billing", "claim-token", now);

        assertThat(claimed).isTrue();
        assertThat(recorded.sql()).containsIgnoringCase("insert into jfoundry_inbox_message")
                .containsIgnoringCase("on conflict (consumer_name, message_id) do nothing");
        assertThat(recorded.parameters()).containsOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(recorded.parameters().get(1)).isInstanceOf(String.class);
        assertThat(recorded.parameters().get(2)).isEqualTo("msg-1");
        assertThat(recorded.parameters().get(3)).isEqualTo("billing");
        assertThat(recorded.parameters().get(4)).isEqualTo("PROCESSING");
        assertThat(recorded.parameters().get(5)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
        assertThat(recorded.parameters().get(6)).isEqualTo("claim-token");
        assertThat(recorded.parameters().get(7)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
        assertThat(recorded.parameters().get(8)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
    }

    @Test
    void mysqlUsesNoOpDuplicateUpdateAndReturnsFalseWhenTheRowAlreadyExists() {
        RecordedNativeQuery recorded = new RecordedNativeQuery(0, false);
        Instant now = Instant.parse("2026-07-16T10:15:30Z");

        boolean claimed = new MySqlJpaInboxClaimStrategy()
                .tryClaim(recorded.entityManager(), "msg-1", "billing", "claim-token", now);

        assertThat(claimed).isFalse();
        assertThat(recorded.sql()).containsIgnoringCase("insert into jfoundry_inbox_message")
                .containsIgnoringCase("on duplicate key update id = id")
                .doesNotContainIgnoringCase("insert ignore");
        assertThat(recorded.parameters()).containsOnlyKeys(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(recorded.parameters().get(1)).isInstanceOf(String.class);
        assertThat(recorded.parameters().get(2)).isEqualTo("msg-1");
        assertThat(recorded.parameters().get(3)).isEqualTo("billing");
        assertThat(recorded.parameters().get(4)).isEqualTo("PROCESSING");
        assertThat(recorded.parameters().get(5)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
        assertThat(recorded.parameters().get(6)).isEqualTo("claim-token");
        assertThat(recorded.parameters().get(7)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
        assertThat(recorded.parameters().get(8)).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 15, 30));
    }

    @Test
    void mysqlReturnsFalseWhenAffectedRowsReportOneButTheGeneratedIdWasNotInserted() {
        RecordedNativeQuery recorded = new RecordedNativeQuery(1, false);

        assertThat(new MySqlJpaInboxClaimStrategy()
                .tryClaim(recorded.entityManager(), "msg-1", "billing", "claim-token", Instant.parse("2026-07-16T10:15:30Z")))
                .isFalse();
        assertThat(recorded.lookupSql()).contains("JpaInboxMessageEntity e where e.id = :generatedId");
        assertThat(recorded.lookupId()).isEqualTo(recorded.parameters().get(1));
    }

    @Test
    void mysqlStrategyClaimsOnceAndPropagatesInvalidRowsInMysqlMode() {
        try (EntityManagerFactory entityManagerFactory = entityManagerFactory("MySQL")) {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                entityManager.getTransaction().begin();

                MySqlJpaInboxClaimStrategy strategy = new MySqlJpaInboxClaimStrategy();
                assertThat(strategy.tryClaim(entityManager, "msg-1", "billing", "token-1", Instant.now())).isTrue();
                assertThat(strategy.tryClaim(entityManager, "msg-1", "billing", "token-2", Instant.now())).isFalse();
                assertThatThrownBy(() -> strategy.tryClaim(entityManager, "msg-2", "x".repeat(256), "token-3", Instant.now()))
                        .hasRootCauseInstanceOf(org.h2.jdbc.JdbcSQLDataException.class);
            } finally {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                entityManager.close();
            }
        }
    }

    private static EntityManagerFactory entityManagerFactory(String mode) {
        return Persistence.createEntityManagerFactory("jfoundry-inbox-jpa-test", Map.of(
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:jfoundry-inbox-jpa-" + mode
                        + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1"));
    }

    private static final class RecordedNativeQuery {

        private final int updateCount;
        private final boolean generatedIdPresent;
        private final Map<Integer, Object> parameters = new LinkedHashMap<>();
        private String sql;
        private String lookupQuery;
        private Object lookupId;

        private RecordedNativeQuery(int updateCount) {
            this(updateCount, true);
        }

        private RecordedNativeQuery(int updateCount, boolean generatedIdPresent) {
            this.updateCount = updateCount;
            this.generatedIdPresent = generatedIdPresent;
        }

        private EntityManager entityManager() {
            return (EntityManager) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{EntityManager.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("createNativeQuery")) {
                            sql = (String) args[0];
                            return nativeQuery();
                        }
                        if (method.getName().equals("createQuery")) {
                            lookupQuery = (String) args[0];
                            return lookupQueryProxy();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Query nativeQuery() {
            return (Query) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Query.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setParameter")) {
                            parameters.put((Integer) args[0], args[1]);
                            return proxy;
                        }
                        if (method.getName().equals("executeUpdate")) {
                            return updateCount;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @SuppressWarnings("unchecked")
        private TypedQuery<Long> lookupQueryProxy() {
            return (TypedQuery<Long>) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{TypedQuery.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("setParameter")) {
                            lookupId = args[1];
                            return proxy;
                        }
                        if (method.getName().equals("getSingleResult")) {
                            return generatedIdPresent && parameters.get(1).equals(lookupId) ? 1L : 0L;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private String sql() {
            return sql;
        }

        private Map<Integer, Object> parameters() {
            return parameters;
        }

        private String lookupSql() {
            return lookupQuery;
        }

        private Object lookupId() {
            return lookupId;
        }
    }
}
