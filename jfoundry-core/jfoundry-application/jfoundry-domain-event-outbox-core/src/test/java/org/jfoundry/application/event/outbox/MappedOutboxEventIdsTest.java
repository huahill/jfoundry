package org.jfoundry.application.event.outbox;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MappedOutboxEventIdsTest {

    @Test
    void derivesStableIdsThatFitTheOutboxEventIdColumn() throws Exception {
        String sourceEventId = UUID.fromString("11111111-1111-1111-1111-111111111111").toString();

        String first = MappedOutboxEventIds.derive(sourceEventId, "sales.order-created.v1", "orders.v1");
        String second = MappedOutboxEventIds.derive(sourceEventId, "sales.order-created.v1", "orders.v1");

        String expectedSuffix = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        "sales.order-created.v1\0orders.v1".getBytes(StandardCharsets.UTF_8)),
                0, 8);
        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo(sourceEventId + ':' + expectedSuffix);
        assertThat(first).hasSize(53);
        assertThat(first.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void changesWhenPayloadTypeOrTopicChanges() {
        String sourceEventId = "evt-1";

        String left = MappedOutboxEventIds.derive(sourceEventId, "sales.order-created.v1", "orders.v1");
        String right = MappedOutboxEventIds.derive(sourceEventId, "sales.order-paid.v1", "orders.v1");
        String otherTopic = MappedOutboxEventIds.derive(sourceEventId, "sales.order-created.v1", "orders.v2");

        assertThat(left).isNotEqualTo(right);
        assertThat(left).isNotEqualTo(otherTopic);
        assertThat(right).isNotEqualTo(otherTopic);
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> MappedOutboxEventIds.derive(null, "type", "topic"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MappedOutboxEventIds.derive("id", null, "topic"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MappedOutboxEventIds.derive("id", "type", null))
                .isInstanceOf(NullPointerException.class);
    }
}
