package org.jfoundry.infrastructure.inbox.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.jfoundry.application.inbox.InboxClaim;
import org.jfoundry.application.inbox.InboxClaimSource;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.application.inbox.InboxMessageStore;

import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.UUID;

public final class MybatisPlusInboxMessageStore implements InboxMessageStore {

    private static final String MESSAGE_ID = "message_id";
    private static final String CONSUMER_NAME = "consumer_name";
    private static final String STATUS = "status";
    private static final String PROCESSED_AT = "processed_at";
    private static final String UPDATED_AT = "updated_at";
    private static final String CLAIMED_AT = "claimed_at";
    private static final String CLAIM_TOKEN = "claim_token";
    private static final String ERROR_MESSAGE = "error_message";

    private final InboxMessageMapper mapper;

    public MybatisPlusInboxMessageStore(InboxMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public InboxClaim claim(String messageId, String consumerName, Instant now, Duration leaseDuration) {
        InboxMessageData existing = mapper.selectOne(new QueryWrapper<InboxMessageData>()
                .eq(MESSAGE_ID, messageId)
                .eq(CONSUMER_NAME, consumerName));
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
            int updated = mapper.update(null, new UpdateWrapper<InboxMessageData>()
                    .set(STATUS, InboxMessageStatus.PROCESSING.name())
                    .set(CLAIMED_AT, now).set(CLAIM_TOKEN, token)
                    .set(UPDATED_AT, now).set(ERROR_MESSAGE, null)
                    .eq(MESSAGE_ID, messageId).eq(CONSUMER_NAME, consumerName)
                    .eq(STATUS, InboxMessageStatus.FAILED.name()));
            return updated == 1 ? InboxClaim.claimed(token, InboxClaimSource.FAILED_RETRY) : InboxClaim.inProgress();
        }
        if (existing.getClaimedAt() == null || !existing.getClaimedAt().plus(leaseDuration).isBefore(now)) {
            return InboxClaim.inProgress();
        }
        int updated = mapper.update(null, new UpdateWrapper<InboxMessageData>()
                .set(STATUS, InboxMessageStatus.PROCESSING.name())
                .set(CLAIMED_AT, now).set(CLAIM_TOKEN, token)
                .set(UPDATED_AT, now).set(ERROR_MESSAGE, null)
                .eq(MESSAGE_ID, messageId).eq(CONSUMER_NAME, consumerName)
                .eq(STATUS, InboxMessageStatus.PROCESSING.name())
                .eq(CLAIMED_AT, existing.getClaimedAt()));
        return updated == 1 ? InboxClaim.claimed(token, InboxClaimSource.EXPIRED_LEASE) : InboxClaim.inProgress();
    }

    @Override
    public boolean markProcessed(String messageId, String consumerName, String claimToken, Instant now) {
        return mapper.update(null, new UpdateWrapper<InboxMessageData>()
                .set(STATUS, InboxMessageStatus.PROCESSED.name()).set(PROCESSED_AT, now)
                .set(UPDATED_AT, now).set(CLAIMED_AT, null).set(CLAIM_TOKEN, null)
                .eq(MESSAGE_ID, messageId).eq(CONSUMER_NAME, consumerName)
                .eq(STATUS, InboxMessageStatus.PROCESSING.name()).eq(CLAIM_TOKEN, claimToken)) == 1;
    }

    @Override
    public boolean markFailed(String messageId, String consumerName, String claimToken, String errorMessage, Instant now) {
        return mapper.update(null, new UpdateWrapper<InboxMessageData>()
                .set(STATUS, InboxMessageStatus.FAILED.name()).set(UPDATED_AT, now)
                .set(ERROR_MESSAGE, errorMessage).set(CLAIMED_AT, null).set(CLAIM_TOKEN, null)
                .eq(MESSAGE_ID, messageId).eq(CONSUMER_NAME, consumerName)
                .eq(STATUS, InboxMessageStatus.PROCESSING.name()).eq(CLAIM_TOKEN, claimToken)) == 1;
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
