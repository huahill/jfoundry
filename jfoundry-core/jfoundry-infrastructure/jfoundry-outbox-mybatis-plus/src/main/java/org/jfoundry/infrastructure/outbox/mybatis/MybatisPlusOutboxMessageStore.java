package org.jfoundry.infrastructure.outbox.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// Default OutboxMessageStore implementation based on MyBatis-Plus.
/// <p>
/// The SPI-level {@link OutboxMessage} carries no ORM annotations. This class performs entry ↔
/// {@link OutboxData} conversion at the boundary. All database operations use standard
/// {@link com.baomidou.mybatisplus.core.mapper.BaseMapper} APIs such as selectPage, update, and
/// deleteBatchIds. Cross-dialect SQL is generated at runtime by MyBatis-Plus and
/// {@link PaginationInnerInterceptor} according to {@link com.baomidou.mybatisplus.annotation.DbType};
/// this class does not maintain database-specific SQL, so adding a dialect does not require source
/// changes here.
/// <p>
/// <b>Atomic multi-instance claim (core)</b>: this implementation avoids MySQL-specific
/// {@code UPDATE...ORDER BY...LIMIT N} and uses {@code selectPage(N) → per-row CAS UPDATE}:
/// <ol>
///   <li>{@code selectPage(N, WHERE status IN (PENDING, FAILED) AND retry-due)} selects candidates;
///       the LIMIT clause is generated per dialect by PaginationInnerInterceptor.</li>
///   <li>Each candidate is claimed with {@code UPDATE...WHERE event_id=? AND status=candidate.status}
///       as a CAS guard. If a concurrent claimer already took the row and changed the status to
///       DISPATCHING, the CAS update affects 0 rows and this row is skipped without sending. CAS
///       UPDATE is standard ANSI SQL and is cross-dialect.</li>
/// </ol>
/// Under H2 READ_COMMITTED, selected rows naturally do not overlap and CAS failures are rare; under
/// REPEATABLE_READ or higher isolation levels, CAS provides the defensive fallback. The
/// {@code claimToken} field is kept and generated per call for operational observability and claim
/// correlation, but the CAS mode no longer relies on token-based readback deduplication.
/// <p>
/// Construction fails fast when the supplied MybatisPlusInterceptor does not contain a
/// PaginationInnerInterceptor.
public class MybatisPlusOutboxMessageStore implements OutboxMessageStore {

    private final OutboxMapper mapper;

    public MybatisPlusOutboxMessageStore(OutboxMapper mapper,
                                       MybatisPlusInterceptor mybatisPlusInterceptor) {
        this.mapper = mapper;
        verifyPaginationInterceptor(mybatisPlusInterceptor);
    }

    private static void verifyPaginationInterceptor(MybatisPlusInterceptor interceptor) {
        boolean hasPagination = interceptor.getInterceptors().stream()
                .anyMatch(PaginationInnerInterceptor.class::isInstance);
        if (!hasPagination) {
            throw new IllegalStateException(
                    "MybatisPlusInterceptor does not contain PaginationInnerInterceptor. "
                            + "OutboxMessageStore relies on selectPage to generate dialect SQL in "
                            + "findDispatchable, claimDispatchable, and deleteByStatusAndOccurredAtBefore. "
                            + "Without it, selectPage can silently return a full table. "
                            + "Please add PaginationInnerInterceptor to MybatisPlusInterceptor.");
        }
    }

    @Override
    public void append(OutboxMessage entry) {
        mapper.insert(OutboxData.fromMessage(entry));
    }

    @Override
    public List<OutboxMessage> findDispatchable(int limit, Instant now) {
        Page<OutboxData> page = new Page<>(1, limit, false);
        IPage<OutboxData> result = mapper.selectPage(page,
                dispatchableCandidatesQuery(now).orderByAsc(OutboxData::getOccurredAt));
        return result.getRecords().stream().map(OutboxData::toMessage).toList();
    }

    /// WHERE condition for dispatchable candidates: {@code status IN (PENDING, FAILED) AND retry-due}.
    /// {@link #findDispatchable} and {@link #claimDispatchable} share this condition and specify
    /// their own orderBy clauses.
    /// <p>
    /// retry-due means {@code nextRetryAt IS NULL} for never-failed rows or {@code nextRetryAt ≤ now}
    /// for rows whose retry time has arrived.
    private static LambdaQueryWrapper<OutboxData> dispatchableCandidatesQuery(Instant now) {
        return Wrappers.lambdaQuery(OutboxData.class)
                .in(OutboxData::getStatus,
                        OutboxMessageStatus.PENDING.name(),
                        OutboxMessageStatus.FAILED.name())
                .and(wrapper -> wrapper
                        .isNull(OutboxData::getNextRetryAt)
                        .or()
                        .le(OutboxData::getNextRetryAt, now));
    }

    @Override
    public void markAsPublished(String eventId) {
        markAsPublished(eventId, null);
    }

    @Override
    public void markAsPublished(String eventId, String claimToken) {
        OutboxData data = mapper.selectById(eventId);
        if (data == null || !OutboxMessageStatus.DISPATCHING.name().equals(data.getStatus())) {
            return;
        }
        if (claimToken != null && !claimToken.equals(data.getClaimToken())) {
            return;
        }
        OutboxMessage entry = OutboxData.toMessage(data);
        entry.markPublished();
        updateClaimedEntry(eventId, claimToken, OutboxData.fromMessage(entry));
    }

    @Override
    public void markAsFailed(String eventId, String errorMessage, int maxRetries, BackoffStrategy backoff) {
        markAsFailed(eventId, null, errorMessage, maxRetries, backoff);
    }

    @Override
    public void markAsFailed(String eventId, String claimToken,
                             String errorMessage, int maxRetries, BackoffStrategy backoff) {
        OutboxData data = mapper.selectById(eventId);
        if (data == null || !OutboxMessageStatus.DISPATCHING.name().equals(data.getStatus())) {
            return;
        }
        if (claimToken != null && !claimToken.equals(data.getClaimToken())) {
            return;
        }
        OutboxMessage entry = OutboxData.toMessage(data);
        entry.markFailed(errorMessage, maxRetries, backoff);
        updateClaimedEntry(eventId, claimToken, OutboxData.fromMessage(entry));
    }

    private void updateClaimedEntry(String eventId, String claimToken, OutboxData data) {
        var update = Wrappers.lambdaUpdate(OutboxData.class)
                .set(OutboxData::getStatus, data.getStatus())
                .set(OutboxData::getRetryCount, data.getRetryCount())
                .set(OutboxData::getErrorMessage, data.getErrorMessage())
                .set(OutboxData::getLastAttemptAt, data.getLastAttemptAt())
                .set(OutboxData::getNextRetryAt, data.getNextRetryAt())
                .set(OutboxData::getUpdatedAt, data.getUpdatedAt())
                .set(OutboxData::getClaimedAt, null)
                .set(OutboxData::getClaimedBy, null)
                .set(OutboxData::getClaimToken, null)
                .eq(OutboxData::getEventId, eventId)
                .eq(OutboxData::getStatus, OutboxMessageStatus.DISPATCHING.name());
        if (claimToken != null) {
            update.eq(OutboxData::getClaimToken, claimToken);
        }
        mapper.update(null, update);
    }

    @Override
    public void reactivate(String eventId) {
        OutboxData data = mapper.selectById(eventId);
        if (data == null) {
            return;
        }
        OutboxMessage entry = OutboxData.toMessage(data);
        entry.reactivate();
        mapper.updateById(OutboxData.fromMessage(entry));
    }

    /// Two-step CAS claim: select candidates with selectPage, then CAS UPDATE each row. The outer
    /// retry loop continues until the limit is reached or the candidate pool is exhausted.
    /// <p>
    /// Candidate semantics match {@link #findDispatchable}: all PENDING rows plus FAILED rows whose
    /// {@code next_retry_at} is due. The CAS guard
    /// {@code WHERE event_id=? AND status=candidate.status} prevents concurrent claimers from taking
    /// the same row. A CAS failure ({@code affectedRows=0}) means another claimer already changed the
    /// row to DISPATCHING, so this row is skipped. All CAS-failed rows are naturally filtered out by
    /// the next selectPage because another claimer has moved them to DISPATCHING. The outer while loop
    /// therefore converges: either it reaches the limit or selectPage returns empty.
    /// <p>
    /// Caller contract: the return size is ≤ {@code limit}. If the candidate pool is smaller, the
    /// method returns fewer rows and the dispatcher can retry on the next cycle.
    @Override
    public List<OutboxMessage> claimDispatchable(int limit, String claimerId) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        if (claimerId == null || claimerId.isBlank()) {
            throw new IllegalArgumentException("claimerId must not be blank");
        }
        // claimToken is kept for operational correlation of this SQL batch, but CAS mode no longer
        // relies on token-based readback deduplication. CAS-failed rows
        // naturally do not appear in the return value.
        String claimToken = UUID.randomUUID().toString();
        Instant now = Instant.now();

        List<OutboxMessage> claimed = new ArrayList<>(limit);
        while (claimed.size() < limit) {
            int remaining = limit - claimed.size();
            // Step 1: select candidates with selectPage; LIMIT is generated per dialect by
            // PaginationInnerInterceptor. Ordering by eventId gives concurrent pods a deterministic
            // CAS order and reduces deadlock probability.
            Page<OutboxData> page = new Page<>(1, remaining, false);
            IPage<OutboxData> result = mapper.selectPage(page,
                    dispatchableCandidatesQuery(now).orderByAsc(OutboxData::getEventId));
            if (result.getRecords().isEmpty()) {
                break;
            }
            // Step 2: per-row CAS UPDATE. WHERE status=candidate.status is the optimistic-lock guard.
            for (OutboxData candidate : result.getRecords()) {
                if (claimed.size() >= limit) {
                    break;
                }
                Instant claimTime = Instant.now();
                int updated = mapper.update(null,
                        Wrappers.lambdaUpdate(OutboxData.class)
                                .set(OutboxData::getStatus, OutboxMessageStatus.DISPATCHING.name())
                                .set(OutboxData::getClaimedBy, claimerId)
                                .set(OutboxData::getClaimToken, claimToken)
                                .set(OutboxData::getClaimedAt, claimTime)
                                .eq(OutboxData::getEventId, candidate.getEventId())
                                .eq(OutboxData::getStatus, candidate.getStatus()));
                if (updated == 1) {
                    // Return values reflect the post-claim state; the DB has been written and the
                    // in-memory candidate copy is synchronized.
                    candidate.setStatus(OutboxMessageStatus.DISPATCHING.name());
                    candidate.setClaimedBy(claimerId);
                    candidate.setClaimToken(claimToken);
                    candidate.setClaimedAt(claimTime);
                    claimed.add(OutboxData.toMessage(candidate));
                }
                // CAS failure (updated=0) means a concurrent claimer already took this row; skip it.
                // The outer while loop selects another candidate batch to fill the remaining limit.
            }
        }
        return claimed;
    }

    /// Stuck-DISPATCHING recovery: rolls DISPATCHING rows with {@code claimed_at < cutoff} back to
    /// PENDING and clears {@code claimed_at / claimed_by / claim_token}.
    /// <p>
    /// The standard {@code UPDATE...WHERE} form is cross-dialect. Rows left in DISPATCHING after a
    /// pod crash or kill -9 are recovered here and will be claimed again by a later dispatcher cycle.
    @Override
    public int recoverStuckDispatching(Instant cutoff) {
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff must not be null");
        }
        return mapper.update(null,
                Wrappers.lambdaUpdate(OutboxData.class)
                        .set(OutboxData::getStatus, OutboxMessageStatus.PENDING.name())
                        .set(OutboxData::getClaimedAt, null)
                        .set(OutboxData::getClaimedBy, null)
                        .set(OutboxData::getClaimToken, null)
                        .eq(OutboxData::getStatus, OutboxMessageStatus.DISPATCHING.name())
                        .lt(OutboxData::getClaimedAt, cutoff));
    }

    /// Batch cleanup: deletes rows in the specified terminal status (PUBLISHED / DEAD_LETTERED) whose
    /// {@code occurred_at} is earlier than {@code cutoff}, at most {@code batchSize} rows per batch,
    /// looping until no candidates remain.
    /// <p>
    /// This uses a selectPage + removeByIds two-step approach: selectPage LIMIT is generated per
    /// dialect by PaginationInnerInterceptor, and removeByIds deletes by primary key using standard
    /// ANSI SQL. It no longer uses {@code DELETE...IN (SELECT...LIMIT)}.
    /// <p>
    /// The loop is intentional. A single large DELETE takes more locks and can affect claim/dispatch
    /// through a long transaction. Batching locks at most batchSize rows per batch and releases locks
    /// between batches so other transactions can proceed. {@code deleted < batchSize} means the
    /// candidate pool is exhausted.
    @Override
    public int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (cutoff == null) {
            throw new IllegalArgumentException("cutoff must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        int total = 0;
        while (true) {
            Page<OutboxData> page = new Page<>(1, batchSize, false);
            IPage<OutboxData> batch = mapper.selectPage(page,
                    Wrappers.lambdaQuery(OutboxData.class)
                            .eq(OutboxData::getStatus, status.name())
                            .lt(OutboxData::getOccurredAt, cutoff)
                            .orderByAsc(OutboxData::getEventId));
            if (batch.getRecords().isEmpty()) {
                break;
            }
            List<String> ids = batch.getRecords().stream()
                    .map(OutboxData::getEventId)
                    .toList();
            int deleted = mapper.deleteByIds(ids);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
