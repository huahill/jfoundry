package org.jfoundry.integration.mysql;

import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.jfoundry.infrastructure.inbox.jpa.MySqlJpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.integration.support.JpaOutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.SqlScripts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = JpaOutboxInboxDatabaseConfig.class, properties = {
        "jfoundry.outbox.dispatcher.mode=none",
        "spring.jpa.hibernate.ddl-auto=none"
})
class MySqlJpaInboxStoreIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry")
            .withCommand("--innodb_lock_wait_timeout=1");

    @Autowired
    private JpaInboxMessageStore store;

    @Autowired
    private JpaInboxClaimStrategy claimStrategy;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @BeforeAll
    static void createSchema(@Autowired DataSource dataSource) {
        SqlScripts.run(dataSource, "jfoundry/sql/inbox/common/create_inbox_message.sql");
    }

    @BeforeEach
    void cleanDb() {
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        jdbcTemplate.update("delete from jfoundry_inbox_message");
    }

    @Test
    void duplicateClaimReturnsFalseAndLeavesTheTransactionUsable() {
        assertThat(claimStrategy).isInstanceOf(MySqlJpaInboxClaimStrategy.class);
        inTransaction(() -> assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue());
        inTransaction(() -> {
            assertThat(store.tryStartProcessing("evt-1", "projection")).isFalse();
            store.markProcessed("evt-1", "projection");
            assertThat(store.isProcessed("evt-1", "projection")).isTrue();
        });
    }

    @Test
    void onlyOneConcurrentWorkerClaimsTheMessage() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger winners = new AtomicInteger();
        var workers = new java.util.ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 2; i++) {
                workers.add(pool.submit(() -> {
                    await(start);
                    if (retryTransientTransaction(() -> store.tryStartProcessing("evt-1", "projection"))) {
                        winners.incrementAndGet();
                    }
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            for (Future<?> worker : workers) {
                worker.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(winners.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from jfoundry_inbox_message", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedMessagesCanRetryWhileProcessedMessagesRemainTerminal() {
        inTransaction(() -> assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue());
        inTransaction(() -> store.markFailed("evt-1", "projection", "broker unavailable"));
        inTransaction(() -> assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue());
        inTransaction(() -> store.markProcessed("evt-1", "projection"));
        inTransaction(() -> assertThat(store.tryStartProcessing("evt-1", "projection")).isFalse());
    }

    private void inTransaction(Runnable action) {
        transactions.executeWithoutResult(ignored -> action.run());
    }

    private <T> T retryTransientTransaction(java.util.function.Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return transactions.execute(ignored -> action.get());
            } catch (RuntimeException exception) {
                if (!(exception instanceof TransientDataAccessException)
                        && !(exception instanceof OptimisticLockException)
                        && !(exception instanceof PessimisticLockException)) {
                    throw exception;
                }
                if (attempt == 4) {
                    throw exception;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent workers", exception);
        }
    }
}
