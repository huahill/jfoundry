package org.jfoundry.autoconfigure.outbox.dispatcher;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import tools.jackson.databind.ObjectMapper;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxData;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.outbox.OutboxDispatcher;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/// Cleanup job must delete PUBLISHED records older than the retention threshold and leave recent
/// PUBLISHED records alone. Same applies to DEAD_LETTERED.
/// <p>
/// Test isolation: the dedicated H2 DB name {@code jfoundry-cleanup-test} avoids sharing
/// state with other autoconfigure tests that use {@code jfoundry-outbox-test} or
/// {@code jfoundry-starter-test}. The dispatcher mode is set to {@code none} so this test
/// only exercises cleanup behavior.
/// <p>
/// Note: {@code OutboxMessageStore.findById(...)} does not exist on the SPI —
/// we verify deletion through {@link OutboxMapper#selectById(String)} instead (same
/// pattern as {@code RecoverStuckDispatchingTest}).
/// <p>
/// Note: {@code OutboxMessage.newBuilder()} does not exist; the real factory is
/// {@link OutboxMessage#newPending}. After {@code append}, the row is in PENDING, so we
/// flip it to the target terminal state via standard {@code lambdaUpdate}. Custom SQL helpers on the
/// mapper were removed; all UPDATE operations are performed through BaseMapper + Wrapper.
@SpringBootTest(classes = OutboxCleanupJobTest.TestApp.class)
@TestPropertySource(properties = {
        "jfoundry.outbox.dispatcher.interval-ms=600000",
        "jfoundry.outbox.cleanup.enabled=false",
        "jfoundry.outbox.cleanup.published-retention-days=7",
        "jfoundry.outbox.cleanup.dead-lettered-retention-days=30",
        "jfoundry.outbox.cleanup.batch-size=100",
        "spring.datasource.url=jdbc:h2:mem:jfoundry-cleanup-test;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:outbox_event.sql"
})
class OutboxCleanupJobTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class TestApp {
        /// DomainEventOutboxRecorderAutoConfiguration's payloadSerializer bean
        /// pulls in Jackson. Same pattern as OutboxTableNameOverrideTest /
        /// PaginationInterceptorDbTypeDefaultTest.
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MessageSender messageSender() {
            return outbound -> SendResult.ok();
        }
    }

    @Autowired
    private OutboxMessageStore repository;

    @Autowired
    private OutboxMapper mapper;

    @Autowired
    private OutboxDispatcher outboxDispatcher;

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
    }

    @Test
    void deletesOnlyOldTerminalRecords() {
        // Seed: 1 PUBLISHED 8 days ago (older than 7-day retention → deleted),
        //       1 PUBLISHED 1 day ago (within 7-day retention → kept).
        seed("evt-old", OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(8 * 86400L));
        seed("evt-recent", OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(86400L));

        int deleted = cleanupJob().runOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.selectById("evt-old")).isNull();
        assertThat(mapper.selectById("evt-recent")).isNotNull();
    }

    @Test
    void deletesDeadLetteredOlderThanRetention() {
        // DEAD_LETTERED has a longer retention (30d) than PUBLISHED (7d).
        // Seed: 1 DEAD_LETTERED 40 days ago (> 30d → deleted),
        //       1 DEAD_LETTERED 10 days ago (< 30d → kept even though > PUBLISHED retention).
        seed("evt-dead-old", OutboxMessageStatus.DEAD_LETTERED, Instant.now().minusSeconds(40L * 86400L));
        seed("evt-dead-recent", OutboxMessageStatus.DEAD_LETTERED, Instant.now().minusSeconds(10L * 86400L));

        int deleted = cleanupJob().runOnce();

        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.selectById("evt-dead-old")).isNull();
        assertThat(mapper.selectById("evt-dead-recent")).isNotNull();
    }

    @Test
    void preservesNonTerminalRecords() {
        // PENDING / DISPATCHING / FAILED records must NEVER be deleted by the cleanup job,
        // regardless of age — only terminal states (PUBLISHED / DEAD_LETTERED) are eligible.
        seed("evt-pending", OutboxMessageStatus.PENDING, Instant.now().minusSeconds(365L * 86400L));
        seed("evt-failed", OutboxMessageStatus.FAILED, Instant.now().minusSeconds(365L * 86400L));

        int deleted = cleanupJob().runOnce();

        assertThat(deleted).isZero();
        assertThat(mapper.selectById("evt-pending")).isNotNull();
        assertThat(mapper.selectById("evt-failed")).isNotNull();
    }

    @Test
    void disabledCleanupIsNoOp() {
        // Re-fetch the properties bean and disable it at runtime (no ApplicationContext restart).
        // Validates the @Scheduled method's short-circuit guard: disabled → return 0, no Repository call.
        seed("evt-disabled", OutboxMessageStatus.PUBLISHED, Instant.now().minusSeconds(365L * 86400L));

        // Direct call with a job whose properties.isEnabled()=false
        OutboxCleanupProperties disabled = new OutboxCleanupProperties();
        disabled.setEnabled(false);
        OutboxCleanupJob disabledJob = new OutboxCleanupJob(repository, disabled);

        int deleted = disabledJob.runOnce();

        assertThat(deleted).isZero();
        assertThat(mapper.selectById("evt-disabled")).isNotNull();
    }

    @Test
    void batchSizeLoopsUntilAllDeleted() {
        // Seed more records than batch-size; repository must loop until all matching records are gone.
        // batch-size=100 (from @TestPropertySource); seed 250 PUBLISHED records 1 year ago.
        Instant yearAgo = Instant.now().minusSeconds(365L * 86400L);
        for (int i = 0; i < 250; i++) {
            seed("evt-batch-" + i, OutboxMessageStatus.PUBLISHED, yearAgo);
        }

        int deleted = cleanupJob().runOnce();

        assertThat(deleted).isEqualTo(250);
        // Spot-check: first and last should be gone.
        assertThat(mapper.selectById("evt-batch-0")).isNull();
        assertThat(mapper.selectById("evt-batch-249")).isNull();
    }

    @Test
    void dispatchesThroughMybatisPlusStoreWithTheDefaultTransactionRunner() {
        seed("evt-dispatch", OutboxMessageStatus.PENDING, Instant.now());

        outboxDispatcher.dispatch(1);

        assertThat(mapper.selectById("evt-dispatch").getStatus()).isEqualTo(OutboxMessageStatus.PUBLISHED.name());
    }

    /// Helper: append a PENDING entry with the given occurredAt, then flip it to the target status.
    /// OutboxMessage.newPending is the only factory; status transitions via markPublished /
    /// markFailed are for the dispatcher path. For the cleanup test we bypass the state machine
    /// and set status directly via {@code lambdaUpdate}. Custom SQL helpers on the mapper were removed.
    private void seed(String id, OutboxMessageStatus status, Instant occurredAt) {
        OutboxMessage entry = OutboxMessage.newPending(
                id, "test.event", null, "test.type", "{}", occurredAt);
        repository.append(entry);
        mapper.update(null,
                Wrappers.lambdaUpdate(OutboxData.class)
                        .set(OutboxData::getStatus, status.name())
                        .eq(OutboxData::getEventId, id));
        // Sanity: verify the seed actually landed in the expected terminal state.
        OutboxData seeded = mapper.selectById(id);
        assertThat(seeded).as("seeded entry must exist with status=%s", status).isNotNull();
        assertThat(seeded.getStatus()).isEqualTo(status.name());
    }

    private OutboxCleanupJob cleanupJob() {
        OutboxCleanupProperties properties = new OutboxCleanupProperties();
        properties.setEnabled(true);
        properties.setPublishedRetentionDays(7);
        properties.setDeadLetteredRetentionDays(30);
        properties.setBatchSize(100);
        return new OutboxCleanupJob(repository, properties);
    }
}
