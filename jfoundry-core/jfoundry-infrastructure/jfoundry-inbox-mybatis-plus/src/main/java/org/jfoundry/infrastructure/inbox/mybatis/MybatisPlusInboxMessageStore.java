package org.jfoundry.infrastructure.inbox.mybatis;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.application.inbox.InboxMessageStore;

import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.UUID;

public final class MybatisPlusInboxMessageStore implements InboxMessageStore {

    private final InboxMessageMapper mapper;

    public MybatisPlusInboxMessageStore(InboxMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration) {
        InboxMessageData existing = mapper.selectOne(Wrappers.lambdaQuery(InboxMessageData.class)
                .eq(InboxMessageData::getMessageId, messageId)
                .eq(InboxMessageData::getConsumerName, consumerName));
        String token = UUID.randomUUID().toString();
        if (existing == null) {
            InboxMessageData data = InboxMessageData.processing(messageId, consumerName);
            data.setClaimedAt(now);
            data.setClaimToken(token);
            try {
                return mapper.insert(data) == 1 ? InboxClaim.fresh(token) : InboxClaim.inProgress();
            } catch (RuntimeException exception) {
                if (isDuplicateKey(exception)) {
                    return InboxClaim.inProgress();
                }
                throw exception;
            }
        }
        if (InboxMessageStatus.PROCESSED.name().equals(existing.getStatus())) {
            return InboxClaim.duplicate();
        }
        if (InboxMessageStatus.FAILED.name().equals(existing.getStatus())) {
            int updated = mapper.update(null, Wrappers.lambdaUpdate(InboxMessageData.class)
                    .set(InboxMessageData::getStatus, InboxMessageStatus.PROCESSING.name())
                    .set(InboxMessageData::getClaimedAt, now).set(InboxMessageData::getClaimToken, token)
                    .set(InboxMessageData::getUpdatedAt, now).set(InboxMessageData::getErrorMessage, null)
                    .eq(InboxMessageData::getMessageId, messageId).eq(InboxMessageData::getConsumerName, consumerName)
                    .eq(InboxMessageData::getStatus, InboxMessageStatus.FAILED.name()));
            return updated == 1 ? InboxClaim.claimed(token, InboxClaimSource.FAILED_RETRY) : InboxClaim.inProgress();
        }
        if (existing.getClaimedAt() == null || !existing.getClaimedAt().plus(leaseDuration).isBefore(now)) {
            return InboxClaim.inProgress();
        }
        int updated = mapper.update(null, Wrappers.lambdaUpdate(InboxMessageData.class)
                .set(InboxMessageData::getStatus, InboxMessageStatus.PROCESSING.name())
                .set(InboxMessageData::getClaimedAt, now).set(InboxMessageData::getClaimToken, token)
                .set(InboxMessageData::getUpdatedAt, now).set(InboxMessageData::getErrorMessage, null)
                .eq(InboxMessageData::getMessageId, messageId).eq(InboxMessageData::getConsumerName, consumerName)
                .eq(InboxMessageData::getStatus, InboxMessageStatus.PROCESSING.name())
                .eq(InboxMessageData::getClaimedAt, existing.getClaimedAt()));
        return updated == 1 ? InboxClaim.claimed(token, InboxClaimSource.EXPIRED_LEASE) : InboxClaim.inProgress();
    }

    @Override
    public boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now) {
        return mapper.update(null, Wrappers.lambdaUpdate(InboxMessageData.class)
                .set(InboxMessageData::getStatus, InboxMessageStatus.PROCESSED.name()).set(InboxMessageData::getProcessedAt, now)
                .set(InboxMessageData::getUpdatedAt, now).set(InboxMessageData::getClaimedAt, null).set(InboxMessageData::getClaimToken, null)
                .eq(InboxMessageData::getMessageId, messageId).eq(InboxMessageData::getConsumerName, consumerName)
                .eq(InboxMessageData::getStatus, InboxMessageStatus.PROCESSING.name()).eq(InboxMessageData::getClaimToken, claimToken)) == 1;
    }

    @Override
    public boolean markFailed(String messageId, String consumerName, String claimToken, String errorMessage, Instant now) {
        return mapper.update(null, Wrappers.lambdaUpdate(InboxMessageData.class)
                .set(InboxMessageData::getStatus, InboxMessageStatus.FAILED.name()).set(InboxMessageData::getUpdatedAt, now)
                .set(InboxMessageData::getErrorMessage, errorMessage).set(InboxMessageData::getClaimedAt, null).set(InboxMessageData::getClaimToken, null)
                .eq(InboxMessageData::getMessageId, messageId).eq(InboxMessageData::getConsumerName, consumerName)
                .eq(InboxMessageData::getStatus, InboxMessageStatus.PROCESSING.name()).eq(InboxMessageData::getClaimToken, claimToken)) == 1;
    }

    private static boolean isDuplicateKey(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null
                    && sqlException.getSQLState().startsWith("23")) {
                return true;
            }
        }
        return false;
    }
}
