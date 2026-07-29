package org.jfoundry.autoconfigure.outbox.dispatcher;

import tools.jackson.databind.ObjectMapper;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxData;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.infrastructure.outbox.spring.dispatcher.ScheduledOutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Two ScheduledOutboxDispatcher instances with different podIds share the same
/// OutboxMessageStore and real H2 database, concurrently dispatch a batch of PENDING records, and
/// assert that each record is passed to MessageSender.send only once. Multi-instance mutual
/// exclusion is guaranteed by claimDispatchable atomicity, not by application-side idempotency.
/// <p>
/// If the dispatcher is changed back to {@code findDispatchable}, which is a read-only SELECT and
/// does not atomically claim records, both pods will read the same PENDING batch and send will be
/// called 2N times. This test would fail.
/// <p>
/// Test isolation: uses a dedicated H2 database name {@code jfoundry-dispatcher-concurrency-test}
/// and sets automatic dispatcher mode to none, avoiding competition between the auto-configured
/// @Scheduled dispatcher and manually constructed test dispatchers.
@SpringBootTest(classes = DispatcherConcurrencyIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "jfoundry.outbox.dispatcher.mode=none",
        "jfoundry.outbox.dispatcher.batch-size=100",
        "spring.datasource.url=jdbc:h2:mem:jfoundry-dispatcher-concurrency-test;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:outbox_event.sql"
})
class DispatcherConcurrencyIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private OutboxMessageStore repository;

    @Autowired
    private OutboxMapper mapper;

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
    }

    @Test
    void twoDispatchersDoNotDoubleDispatch() throws Exception {
        int total = 100;
        for (int i = 0; i < total; i++) {
            // Distinct payload per entry so the sender can dedup.
            repository.append(OutboxMessage.newPending(
                    "evt-" + i, "topic", null, "type", "{\"id\":\"evt-" + i + "\"}", Instant.now()));
        }

        CountingSender sender = new CountingSender();
        BackoffStrategy backoff = failedAttempts -> Duration.ofSeconds(1);

        // Two dispatchers with explicit podIds (package-private constructor).
        ScheduledOutboxDispatcher podA =
                new ScheduledOutboxDispatcher(repository, sender, 5, backoff, total, "pod-A");
        ScheduledOutboxDispatcher podB =
                new ScheduledOutboxDispatcher(repository, sender, 5, backoff, total, "pod-B");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        try {
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                podA.dispatch(total);
                done.countDown();
            });
            pool.submit(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                podB.dispatch(total);
                done.countDown();
            });
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("both dispatchers should finish within 30s").isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(sender.sendCount.get())
                .as("every record must be sent exactly once across both dispatchers")
                .isEqualTo(total);
        assertThat(sender.seenPayloads)
                .as("no record should appear twice in the send stream")
                .hasSize(total);
    }

    @Test
    void twoDispatchersClaimDisjointSetsSequentially() {
        // Sequential sanity check: pod A claims first, pod B claims next — B should see
        // the remaining records A didn't claim (claim → DISPATCHING excludes them).
        for (int i = 0; i < 20; i++) {
            repository.append(OutboxMessage.newPending(
                    "evt-" + i, "topic", null, "type", "{\"id\":\"evt-" + i + "\"}", Instant.now()));
        }

        CountingSender sender = new CountingSender();
        BackoffStrategy backoff = failedAttempts -> Duration.ofSeconds(1);
        ScheduledOutboxDispatcher podA =
                new ScheduledOutboxDispatcher(repository, sender, 5, backoff, 10, "pod-A");
        ScheduledOutboxDispatcher podB =
                new ScheduledOutboxDispatcher(repository, sender, 5, backoff, 10, "pod-B");

        podA.dispatch(10);
        podB.dispatch(10);

        // 10 + 10 = 20 records, each sent once.
        assertThat(sender.sendCount.get()).isEqualTo(20);
        assertThat(sender.seenPayloads).hasSize(20);

        // No record left in claimable state.
        OutboxData check = mapper.selectById("evt-0");
        assertThat(check.getStatus()).isEqualTo("PUBLISHED");
    }

    static class CountingSender implements MessageSender {
        final AtomicInteger sendCount = new AtomicInteger();
        final Set<String> seenPayloads = Collections.newSetFromMap(new ConcurrentHashMap<>());

        @Override
        public SendResult send(org.jfoundry.application.messaging.OutboundMessage message) {
            sendCount.incrementAndGet();
            seenPayloads.add(message.payload());
            return SendResult.ok();
        }
    }
}
