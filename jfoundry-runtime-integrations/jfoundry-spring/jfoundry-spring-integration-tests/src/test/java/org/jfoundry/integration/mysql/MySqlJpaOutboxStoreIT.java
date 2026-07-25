package org.jfoundry.integration.mysql;

import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.infrastructure.outbox.jpa.JpaOutboxMessageStore;
import org.jfoundry.integration.support.JpaOutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.OutboxMessages;
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
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = JpaOutboxInboxDatabaseConfig.class, properties = {
        "jfoundry.outbox.dispatcher.mode=none",
        "spring.jpa.hibernate.ddl-auto=none"
})
class MySqlJpaOutboxStoreIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry")
            .withCommand("--max_allowed_packet=16M", "--innodb_lock_wait_timeout=1");

    @Autowired
    private JpaOutboxMessageStore store;

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
        SqlScripts.run(dataSource, "jfoundry/sql/outbox/mysql/create_outbox_event.sql");
    }

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("delete from jfoundry_outbox_event");
    }

    @Test
    void concurrentClaimLoserReturnsAtMySqlDefaultRepeatableReadIsolation() throws Exception {
        assertThat(value("select @@transaction_isolation")).isEqualTo("REPEATABLE-READ");
        inTransaction(() -> {
            store.append(OutboxMessages.pending("evt-1"));
            return null;
        });
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<String> claimedIds = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> workers = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                int worker = i;
                workers.add(pool.submit(() -> {
                    await(start);
                    claimedIds.addAll(retryTransientTransaction(() -> store.claimDispatchable(1, "pod-" + worker))
                            .stream().map(OutboxMessage::getEventId).toList());
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
        assertThat(claimedIds).containsExactly("evt-1");
        assertThat(claimedIds).doesNotHaveDuplicates();
    }

    @Test
    void currentClaimTokenGuardsPublication() {
        inTransaction(() -> {
            store.append(OutboxMessages.pending("evt-1"));
            return null;
        });
        OutboxMessage firstClaim = inTransaction(() -> store.claimDispatchable(1, "pod-a").getFirst());
        inTransaction(() -> store.recoverStuckDispatching(Instant.now().plusSeconds(1)));
        OutboxMessage secondClaim = inTransaction(() -> store.claimDispatchable(1, "pod-b").getFirst());

        inTransaction(() -> {
            store.markAsPublished("evt-1", firstClaim.getClaimToken());
            return null;
        });
        assertThat(value("select status from jfoundry_outbox_event where event_id = 'evt-1'"))
                .isEqualTo(OutboxMessageStatus.DISPATCHING.name());
        assertThat(value("select claim_token from jfoundry_outbox_event where event_id = 'evt-1'"))
                .isEqualTo(secondClaim.getClaimToken());

        inTransaction(() -> {
            store.markAsPublished("evt-1", secondClaim.getClaimToken());
            return null;
        });
        assertThat(value("select status from jfoundry_outbox_event where event_id = 'evt-1'"))
                .isEqualTo(OutboxMessageStatus.PUBLISHED.name());
    }

    @Test
    void recoveryReleasesStuckClaimsAndCleanupDeletesOnlyTerminalMessages() {
        inTransaction(() -> {
            store.append(OutboxMessages.pending("evt-1"));
            store.append(OutboxMessages.pending("evt-active"));
            return null;
        });
        inTransaction(() -> store.claimDispatchable(1, "pod-a"));

        assertThat(inTransaction(() -> store.recoverStuckDispatching(Instant.now().plusSeconds(1)))).isEqualTo(1);
        OutboxMessage recoveredClaim = inTransaction(() -> store.claimDispatchable(1, "pod-b").getFirst());
        inTransaction(() -> {
            store.markAsPublished("evt-1", recoveredClaim.getClaimToken());
            return null;
        });

        assertThat(inTransaction(() -> store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, Instant.now().plusSeconds(1), 1))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from jfoundry_outbox_event", Integer.class)).isEqualTo(1);
        assertThat(value("select status from jfoundry_outbox_event where event_id = 'evt-active'"))
                .isEqualTo(OutboxMessageStatus.PENDING.name());
    }

    private <T> T inTransaction(Supplier<T> action) {
        return transactions.execute(ignored -> action.get());
    }

    private <T> T retryTransientTransaction(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return inTransaction(action);
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

    private String value(String sql) {
        return jdbcTemplate.queryForObject(sql, String.class);
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
