package org.jfoundry.autoconfigure.outbox.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// {@code jfoundry.outbox.table-name} must redirect OutboxData persistence to a custom-named
/// physical table. The {@link OutboxMybatisPlusAutoConfiguration} registers a
/// {@code DynamicTableNameInnerInterceptor} that rewrites the logical name
/// {@code jfoundry_outbox_event} to whatever business configures.
/// <p>
/// Side assertion: the default {@code jfoundry_outbox_event} table (also created by the test
/// fixture) must stay empty — proving the rewrite happened, not that we widened the write.
/// <p>
/// Coverage includes append and the operational paths claimDispatchable, recoverStuckDispatching,
/// and deleteByStatusAndOccurredAtBefore. It ensures {@code TableNameHandler} rewriting also
/// applies to custom mapper SQL ({@code @Update}/{@code @Select}/{@code @Delete}), not only
/// standard BaseMapper CRUD.
/// <p>
/// Isolation: this test brings up the full autoconfig chain, so it sets dispatcher mode to none to
/// avoid polling interacting with downstream tests and uses a dedicated in-memory H2 name. This test
/// stays in autoconfigure because it specifically exercises the autoconfig-layer
/// {@code TableNameHandler} wiring.
@SpringBootTest(
        classes = OutboxTableNameOverrideTest.TestApp.class,
        properties = {
                "jfoundry.outbox.table-name=custom_outbox",
                // Set dispatcher mode to none so this test exercises only the persistence
                // layer (append → TableNameHandler → custom_outbox). Otherwise the full
                // autoconfig chain starts a ScheduledOutboxDispatcher whose polling may
                // interact with subsequent tests sharing the same H2 instance.
                "jfoundry.outbox.dispatcher.mode=none",
                "spring.autoconfigure.exclude=org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration",
                // Dedicated in-memory DB name for isolation from DomainEventExternalizationIntegrationTest.
                "spring.datasource.url=jdbc:h2:mem:jfoundry-table-name-override;DB_CLOSE_DELAY=-1",
                "spring.sql.init.schema-locations=classpath:outbox_event.sql"
        }
)
@Sql(scripts = "classpath:outbox_custom_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OutboxTableNameOverrideTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class TestApp {
        /// DomainEventOutboxRecorderAutoConfiguration's payloadSerializer bean
        /// pulls in Jackson. Same pattern as OutboxDispatcherEnabledTest / DomainEventExternalizationIntegrationTest.
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private OutboxMessageStore repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        // DB_CLOSE_DELAY=-1 keeps H2 data across tests, so @BeforeEach must clear both tables.
        // Otherwise evt-custom written by appendWritesToCustomTable pollutes the next test's
        // claim/assertion.
        jdbc.update("DELETE FROM custom_outbox");
        jdbc.update("DELETE FROM jfoundry_outbox_event");
    }

    @Test
    void appendWritesToCustomTable() {
        OutboxMessage entry = OutboxMessage.newPending(
                "evt-custom", "test.event", null, "test.type", "{}", Instant.now());
        repository.append(entry);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_outbox WHERE event_id = ?",
                Integer.class, "evt-custom");
        assertThat(count).isEqualTo(1);

        Integer defaultCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jfoundry_outbox_event WHERE event_id = ?",
                Integer.class, "evt-custom");
        assertThat(defaultCount)
                .as("default table must be empty when table-name override is set")
                .isEqualTo(0);
    }

    /// claimDispatchable custom SQL ({@code UPDATE...LIMIT} plus the following SELECT) must also be
    /// rewritten to custom_outbox by {@code DynamicTableNameInnerInterceptor};
    /// otherwise traffic goes to the default table and silently claims no records.
    @Test
    void claimDispatchableReadsFromCustomTable() {
        appendPending("evt-claim-1", "custom_outbox");
        appendPending("evt-claim-2", "custom_outbox");

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-custom");

        assertThat(claimed).extracting(OutboxMessage::getEventId)
                .containsExactlyInAnyOrder("evt-claim-1", "evt-claim-2");

        // Confirm both sides: custom_outbox status changed to DISPATCHING; jfoundry_outbox_event is empty.
        Integer dispatchingInCustom = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_outbox WHERE status = 'DISPATCHING'",
                Integer.class);
        assertThat(dispatchingInCustom).isEqualTo(2);

        Integer anyInDefault = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jfoundry_outbox_event",
                Integer.class);
        assertThat(anyInDefault)
                .as("claim UPDATE/SELECT must also use custom_outbox and leave the default table empty")
                .isEqualTo(0);
    }

    /// recoverStuckDispatching's {@code UPDATE...WHERE status='DISPATCHING' AND claimed_at < cutoff}
    /// must be rewritten to
    /// custom_outbox; otherwise the recovery job silently does nothing.
    @Test
    void recoverStuckDispatchingOperatesOnCustomTable() {
        // Build one stale DISPATCHING record in custom_outbox.
        appendPending("evt-stuck", "custom_outbox");
        repository.claimDispatchable(1, "pod-stuck");
        // Age claimed_at directly to 10 minutes ago to simulate a stale claim after pod crash.
        jdbc.update(
                "UPDATE custom_outbox SET claimed_at = ? WHERE event_id = ?",
                Instant.now().minus(Duration.ofMinutes(10)), "evt-stuck");

        int recovered = repository.recoverStuckDispatching(Instant.now().minus(Duration.ofMinutes(5)));

        assertThat(recovered).isEqualTo(1);

        String status = jdbc.queryForObject(
                "SELECT status FROM custom_outbox WHERE event_id = ?",
                String.class, "evt-stuck");
        assertThat(status).isEqualTo(OutboxMessageStatus.PENDING.name());

        // The default table receives no traffic throughout the test.
        Integer anyInDefault = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jfoundry_outbox_event",
                Integer.class);
        assertThat(anyInDefault).isEqualTo(0);
    }

    /// deleteByStatusAndOccurredAtBefore's subquery + LIMIT DELETE form must be rewritten to
    /// custom_outbox; otherwise the cleanup job silently does nothing. The default table is already
    /// empty, so DELETE returns 0 without an error, which is hard to notice.
    @Test
    void deleteByStatusAndOccurredAtBeforeOperatesOnCustomTable() {
        // Build one record that is PUBLISHED and whose occurred_at is older than cutoff.
        appendPending("evt-published-old", "custom_outbox");
        // Change status through direct SQL, bypassing the markPublished state machine; this test only
        // cares about DELETE rewriting.
        jdbc.update(
                "UPDATE custom_outbox SET status = ?, occurred_at = ? WHERE event_id = ?",
                OutboxMessageStatus.PUBLISHED.name(),
                Instant.now().minus(Duration.ofDays(10)),
                "evt-published-old");
        // One fresh PUBLISHED record should not be cleaned.
        appendPending("evt-published-fresh", "custom_outbox");
        jdbc.update(
                "UPDATE custom_outbox SET status = ? WHERE event_id = ?",
                OutboxMessageStatus.PUBLISHED.name(), "evt-published-fresh");

        int deleted = repository.deleteByStatusAndOccurredAtBefore(
                OutboxMessageStatus.PUBLISHED, Instant.now().minus(Duration.ofDays(1)), 10);

        assertThat(deleted).isEqualTo(1);

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_outbox WHERE event_id = ?",
                Integer.class, "evt-published-old");
        assertThat(remaining).isEqualTo(0);

        Integer freshRemaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_outbox WHERE event_id = ?",
                Integer.class, "evt-published-fresh");
        assertThat(freshRemaining)
                .as("fresh PUBLISHED records should not be cleaned")
                .isEqualTo(1);

        Integer anyInDefault = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jfoundry_outbox_event",
                Integer.class);
        assertThat(anyInDefault).isEqualTo(0);
    }

    private void appendPending(String eventId, String table) {
        OutboxMessage entry = OutboxMessage.newPending(
                eventId, "test.event", null, "test.type", "{}", Instant.now());
        repository.append(entry);
    }
}
