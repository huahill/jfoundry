package org.jfoundry.integration.postgresql;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimResult;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageData;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.jfoundry.integration.support.PostgreSqlOutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.SqlScripts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = PostgreSqlOutboxInboxDatabaseConfig.class, properties = "jfoundry.outbox.dispatcher.mode=none")
class PostgreSqlInboxStoreIT {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jfoundry").withUsername("jfoundry").withPassword("jfoundry");

    @Autowired private MybatisPlusInboxMessageStore store;
    @Autowired private InboxMessageMapper mapper;

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
    void cleanDb() { mapper.delete(null); }

    @Test
    void concurrentWorkersAcquireOnlyOneLease() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicInteger winners = new AtomicInteger();
        try {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (claim("evt-1", "projection").result() == InboxClaimResult.CLAIMED) {
                            winners.incrementAndGet();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(winners.get()).isEqualTo(1);
        assertThat(mapper.selectCount(null)).isEqualTo(1);
    }

    @Test
    void terminalCompletionAndFailureRetryUseClaimTokens() {
        InboxClaim first = claim("evt-1", "projection");
        assertThat(store.markProcessed("evt-1", "projection", "stale", Instant.now())).isFalse();
        assertThat(store.markFailed("evt-1", "projection", first.claimToken(), "boom", Instant.now())).isTrue();

        InboxClaim retry = claim("evt-1", "projection");

        assertThat(retry.source()).isEqualTo(InboxClaimSource.FAILED_RETRY);
        assertThat(store.markProcessed("evt-1", "projection", retry.claimToken(), Instant.now())).isTrue();
        assertThat(load("evt-1", "projection").getStatus()).isEqualTo(InboxMessageStatus.PROCESSED.name());
        assertThat(claim("evt-1", "projection").result()).isEqualTo(InboxClaimResult.DUPLICATE);
    }

    @Test
    void expiredLeaseRejectsTheStaleOwner() {
        Instant firstAt = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim first = store.claim("evt-1", "projection", firstAt, LEASE);
        InboxClaim replacement = store.claim("evt-1", "projection", firstAt.plus(LEASE).plusSeconds(1), LEASE);

        assertThat(replacement.source()).isEqualTo(InboxClaimSource.EXPIRED_LEASE);
        assertThat(store.markProcessed("evt-1", "projection", first.claimToken(), Instant.now())).isFalse();
        assertThat(store.markProcessed("evt-1", "projection", replacement.claimToken(), Instant.now())).isTrue();
    }

    private InboxClaim claim(String messageId, String consumerName) {
        return store.claim(messageId, consumerName, Instant.now(), LEASE);
    }

    private InboxMessageData load(String messageId, String consumerName) {
        return mapper.selectOne(Wrappers.lambdaQuery(InboxMessageData.class)
                .eq(InboxMessageData::getMessageId, messageId)
                .eq(InboxMessageData::getConsumerName, consumerName));
    }
}
