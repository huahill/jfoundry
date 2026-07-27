package org.jfoundry.integration.postgresql;

import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimResult;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxClaimStrategy;
import org.jfoundry.infrastructure.inbox.jpa.JpaInboxMessageStore;
import org.jfoundry.infrastructure.inbox.jpa.PostgreSqlJpaInboxClaimStrategy;
import org.jfoundry.integration.support.JpaOutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.SqlScripts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = JpaOutboxInboxDatabaseConfig.class, properties = {
        "jfoundry.outbox.dispatcher.mode=none", "spring.jpa.hibernate.ddl-auto=none"
})
class PostgreSqlJpaInboxStoreIT {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jfoundry").withUsername("jfoundry").withPassword("jfoundry");

    @Autowired private JpaInboxMessageStore store;
    @Autowired private JpaInboxClaimStrategy claimStrategy;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeAll
    static void createSchema(@Autowired DataSource dataSource) {
        SqlScripts.run(dataSource, "jfoundry/sql/inbox/common/create_inbox_message.sql");
    }

    @BeforeEach
    void cleanDb() { jdbcTemplate.update("delete from jfoundry_inbox_message"); }

    @Test
    void claimsOnceAndCompletesOnlyWithTheOwningToken() {
        assertThat(claimStrategy).isInstanceOf(PostgreSqlJpaInboxClaimStrategy.class);
        InboxClaim first = inTransactionResult(() -> claim("evt-1", "projection"));

        inTransaction(() -> {
            assertThat(claim("evt-1", "projection").result()).isEqualTo(InboxClaimResult.IN_PROGRESS);
            assertThat(store.markProcessed("evt-1", "projection", first.claimToken(), Instant.now())).isTrue();
        });

        assertThat(inTransactionResult(() -> claim("evt-1", "projection").result()))
                .isEqualTo(InboxClaimResult.DUPLICATE);
    }

    @Test
    void onlyOneConcurrentWorkerClaimsTheMessage() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger winners = new AtomicInteger();
        var workers = new java.util.ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 4; i++) {
                workers.add(pool.submit(() -> {
                    await(start);
                    if (inTransactionResult(() -> claim("evt-1", "projection")).result()
                            == InboxClaimResult.CLAIMED) winners.incrementAndGet();
                }));
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            for (Future<?> worker : workers) worker.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(winners.get()).isEqualTo(1);
    }

    @Test
    void failedMessagesCanBeClaimedAgainWithNewOwnership() {
        InboxClaim first = inTransactionResult(() -> claim("evt-1", "projection"));
        inTransaction(() -> assertThat(store.markFailed(
                "evt-1", "projection", first.claimToken(), "broker unavailable", Instant.now())).isTrue());

        InboxClaim retry = inTransactionResult(() -> claim("evt-1", "projection"));

        assertThat(retry.source()).isEqualTo(InboxClaimSource.FAILED_RETRY);
        assertThat(retry.claimToken()).isNotEqualTo(first.claimToken());
    }

    private InboxClaim claim(String messageId, String consumerName) {
        return store.claim(messageId, consumerName, Instant.now(), LEASE);
    }

    private void inTransaction(Runnable action) { transactions.executeWithoutResult(ignored -> action.run()); }

    private <T> T inTransactionResult(java.util.function.Supplier<T> action) {
        return transactions.execute(ignored -> action.get());
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent workers", exception);
        }
    }
}
