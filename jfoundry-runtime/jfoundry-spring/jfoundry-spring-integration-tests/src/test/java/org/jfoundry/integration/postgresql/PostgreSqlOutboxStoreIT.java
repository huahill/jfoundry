package org.jfoundry.integration.postgresql;

import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.jfoundry.infrastructure.outbox.mybatis.MybatisPlusOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxData;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.jfoundry.integration.support.OutboxMessages;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = PostgreSqlOutboxInboxDatabaseConfig.class,
        properties = "jfoundry.outbox.dispatcher.mode=none"
)
class PostgreSqlOutboxStoreIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Autowired
    private MybatisPlusOutboxMessageStore store;

    @Autowired
    private OutboxMapper mapper;

    private final BackoffStrategy fixedBackoff = attempts -> Duration.ofMillis(50);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @BeforeAll
    static void createSchema(@Autowired DataSource dataSource) {
        SqlScripts.run(dataSource, "jfoundry/sql/outbox/postgresql/create_outbox_event.sql");
    }

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
    }

    @Test
    void claimDispatchableClaimsEachMessageOnceUnderConcurrency() throws Exception {
        store.append(OutboxMessages.pending("evt-1"));
        store.append(OutboxMessages.pending("evt-2"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<String> claimedIds = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < 4; i++) {
                int worker = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        store.claimDispatchable(1, "pod-" + worker)
                                .forEach(message -> claimedIds.add(message.getEventId()));
                    } catch (InterruptedException e) {
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

        assertThat(claimedIds).containsExactlyInAnyOrder("evt-1", "evt-2");
        assertThat(claimedIds).doesNotHaveDuplicates();
        assertThat(mapper.selectById("evt-1").getStatus()).isEqualTo("DISPATCHING");
        assertThat(mapper.selectById("evt-2").getStatus()).isEqualTo("DISPATCHING");
    }

    @Test
    void markAsPublishedRequiresCurrentClaimToken() {
        store.append(OutboxMessages.pending("evt-1"));
        OutboxMessage firstClaim = store.claimDispatchable(1, "pod-a").get(0);
        store.recoverStuckDispatching(Instant.now().plusSeconds(1));
        OutboxMessage secondClaim = store.claimDispatchable(1, "pod-b").get(0);

        store.markAsPublished("evt-1", firstClaim.getClaimToken());

        OutboxData data = mapper.selectById("evt-1");
        assertThat(data.getStatus()).isEqualTo("DISPATCHING");
        assertThat(data.getClaimToken()).isEqualTo(secondClaim.getClaimToken());

        store.markAsPublished("evt-1", secondClaim.getClaimToken());

        OutboxData published = mapper.selectById("evt-1");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(published.getClaimToken()).isNull();
        assertThat(published.getClaimedAt()).isNull();
        assertThat(published.getClaimedBy()).isNull();
    }

    @Test
    void markAsFailedRequiresCurrentClaimToken() {
        store.append(OutboxMessages.pending("evt-1"));
        OutboxMessage firstClaim = store.claimDispatchable(1, "pod-a").get(0);
        store.recoverStuckDispatching(Instant.now().plusSeconds(1));
        OutboxMessage secondClaim = store.claimDispatchable(1, "pod-b").get(0);

        store.markAsFailed("evt-1", firstClaim.getClaimToken(), "boom", 5, fixedBackoff);

        OutboxData data = mapper.selectById("evt-1");
        assertThat(data.getStatus()).isEqualTo("DISPATCHING");
        assertThat(data.getClaimToken()).isEqualTo(secondClaim.getClaimToken());
        assertThat(data.getErrorMessage()).isNull();

        store.markAsFailed("evt-1", secondClaim.getClaimToken(), "broker down", 5, fixedBackoff);

        OutboxData failed = mapper.selectById("evt-1");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getErrorMessage()).isEqualTo("broker down");
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getClaimToken()).isNull();
        assertThat(failed.getClaimedAt()).isNull();
        assertThat(failed.getClaimedBy()).isNull();
    }

    @Test
    void recoverStuckDispatchingReleasesExpiredClaims() {
        store.append(OutboxMessages.pending("evt-1"));
        store.claimDispatchable(1, "pod-a");
        OutboxData claimed = mapper.selectById("evt-1");
        claimed.setClaimedAt(Instant.now().minus(Duration.ofMinutes(10)));
        mapper.updateById(claimed);

        int recovered = store.recoverStuckDispatching(Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(recovered).isEqualTo(1);
        OutboxData data = mapper.selectById("evt-1");
        assertThat(data.getStatus()).isEqualTo("PENDING");
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
        assertThat(store.claimDispatchable(1, "pod-b")).hasSize(1);
    }

    @Test
    void deleteByStatusAndOccurredAtBeforeKeepsRecentAndActiveMessages() {
        Instant old = Instant.now().minus(Duration.ofDays(10));
        Instant recent = Instant.now();
        store.append(OutboxMessages.pending("published-old", "topic", null, "{}"));
        store.append(OutboxMessages.pending("published-recent", "topic", null, "{}"));
        store.append(OutboxMessages.pending("pending-old", "topic", null, "{}"));
        OutboxData oldPublished = mapper.selectById("published-old");
        oldPublished.setOccurredAt(old);
        oldPublished.setStatus("PUBLISHED");
        mapper.updateById(oldPublished);
        OutboxData recentPublished = mapper.selectById("published-recent");
        recentPublished.setOccurredAt(recent);
        recentPublished.setStatus("PUBLISHED");
        mapper.updateById(recentPublished);
        OutboxData oldPending = mapper.selectById("pending-old");
        oldPending.setOccurredAt(old);
        mapper.updateById(oldPending);

        int deleted = store.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, Instant.now().minus(Duration.ofDays(1)), 1);

        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.selectById("published-old")).isNull();
        assertThat(mapper.selectById("published-recent")).isNotNull();
        assertThat(mapper.selectById("pending-old")).isNotNull();
    }

    @Test
    void largePayloadRoundTripsThroughPostgreSql() {
        String payload = OutboxMessages.payloadOfSize(1024 * 1024 + 128);

        store.append(OutboxMessages.pending("evt-large", "topic", "key", payload));

        OutboxMessage loaded = store.findDispatchable(1, Instant.now()).get(0);
        assertThat(loaded.getPayloadJson()).isEqualTo(payload);
    }
}
