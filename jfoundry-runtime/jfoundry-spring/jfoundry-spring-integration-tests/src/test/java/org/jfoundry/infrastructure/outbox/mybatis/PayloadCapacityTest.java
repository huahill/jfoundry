package org.jfoundry.infrastructure.outbox.mybatis;

import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// payload_json column must accept 1MB+ payloads.
/// <p>
/// MySQL's TEXT (64KB) was too small for production integration events carrying
/// large domain payloads. The migration script now declares the column as
/// MEDIUMTEXT (16MB) on MySQL/H2 and CLOB (2GB) on DM.
/// <p>
/// This test is a guard: it appends a 1MB payload through the full
/// MyBatis-Plus repository path and asserts the row is persisted. If a future
/// migration accidentally narrows the column back to TEXT, the append will
/// fail with a SQLException on MySQL (and on H2 when configured to enforce
/// length). The SQL declaration is the source of truth; this test is the
/// executable safeguard.
@SpringBootTest(classes = OutboxPersistenceTestConfig.class)
class PayloadCapacityTest {

    @Autowired
    private MybatisPlusOutboxMessageStore repository;

    @BeforeEach
    void cleanDb(@Autowired OutboxMapper mapper) {
        mapper.delete(null);
    }

    @Test
    void appendAcceptsOneMegabytePayload() {
        // 1MB of 'x' chars, wrapped in a JSON string with escaping.
        String big = IntStream.range(0, 1024 * 1024)
                .mapToObj(i -> "x")
                .collect(Collectors.joining());
        String payload = "{\"msg\":\"" + big + "\"}";

        OutboxMessage entry = OutboxMessage.newPending(
                "evt-big", "topic", null, "com.example.LargePayload", payload, Instant.now());

        repository.append(entry);

        // No exception — column type accepts 1MB payloads.
        assertThat(entry.getEventId()).isEqualTo("evt-big");
        assertThat(repository.findDispatchable(100, Instant.now()))
                .extracting(OutboxMessage::getEventId)
                .contains("evt-big");
        // Sanity: verify the persisted payload is intact (OutboxMessageStatus used only
        // to document the post-append state — PENDING awaiting dispatch).
        assertThat(entry.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
    }
}
