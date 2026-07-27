package org.jfoundry.infrastructure.inbox.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimResult;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = InboxPersistenceTestConfig.class)
class MybatisPlusInboxMessageStoreTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired private MybatisPlusInboxMessageStore store;
    @Autowired private InboxMessageMapper mapper;

    @BeforeEach void cleanDb() { mapper.delete(null); }

    @Test
    void freshClaimCanBeCompletedOnlyByItsToken() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim claim = store.claim("evt-1", "projection", now, LEASE);

        assertThat(claim.result()).isEqualTo(InboxClaimResult.CLAIMED);
        assertThat(claim.source()).isEqualTo(InboxClaimSource.FRESH);
        assertThat(store.markProcessed("evt-1", "projection", "stale", now)).isFalse();
        assertThat(store.markProcessed("evt-1", "projection", claim.claimToken(), now)).isTrue();
        assertThat(store.claim("evt-1", "projection", now, LEASE).result()).isEqualTo(InboxClaimResult.DUPLICATE);
    }

    @Test
    void expiredLeaseCanBeReclaimedAndRejectsStaleCompletion() {
        Instant first = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim initial = store.claim("evt-1", "projection", first, LEASE);

        InboxClaim replacement = store.claim("evt-1", "projection", first.plus(LEASE).plusSeconds(1), LEASE);

        assertThat(replacement.source()).isEqualTo(InboxClaimSource.EXPIRED_LEASE);
        assertThat(store.markProcessed("evt-1", "projection", initial.claimToken(), first)).isFalse();
        assertThat(store.markProcessed("evt-1", "projection", replacement.claimToken(), first)).isTrue();
    }

    @Test
    void failedMessageIsClaimedForBrokerRedelivery() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        InboxClaim initial = store.claim("evt-1", "projection", now, LEASE);
        assertThat(store.markFailed("evt-1", "projection", initial.claimToken(), "broker unavailable", now)).isTrue();

        InboxClaim retry = store.claim("evt-1", "projection", now.plusSeconds(1), LEASE);

        assertThat(retry.source()).isEqualTo(InboxClaimSource.FAILED_RETRY);
        assertThat(mapper.selectOne(Wrappers.lambdaQuery(InboxMessageData.class)
                .eq(InboxMessageData::getMessageId, "evt-1")).getStatus())
                .isEqualTo(InboxMessageStatus.PROCESSING.name());
    }
}
