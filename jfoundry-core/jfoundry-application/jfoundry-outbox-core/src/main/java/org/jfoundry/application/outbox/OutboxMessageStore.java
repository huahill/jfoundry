package org.jfoundry.application.outbox;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/// Outbox persistence SPI.
/// <p>
/// Implementation contract for the read-then-update mode:
/// <ul>
///   <li>{@link #markAsPublished(String)} / {@link #markAsFailed(String, String, int, BackoffStrategy)}
///       / {@link #reactivate(String)} are load -> mutate -> save operations. Missing entries return silently.</li>
///   <li>{@link #reactivate(String)} delegates non-DEAD_LETTERED failures to
///       {@link OutboxMessage#reactivate()}, which throws {@link IllegalStateException}.</li>
/// </ul>
/// Multi-instance safety does not rely on distributed locks; consumers should
/// still remain idempotent.
public interface OutboxMessageStore {

    void append(OutboxMessage entry);

    /// Finds dispatchable entries:
    /// <pre>
    /// status IN (PENDING, FAILED) AND (next_retry_at IS NULL OR next_retry_at <= now)
    /// ORDER BY occurredAt ASC
    /// LIMIT n
    /// </pre>
    List<OutboxMessage> findDispatchable(int limit, Instant now);

    void markAsPublished(String eventId);

    default void markAsPublished(String eventId, @Nullable String claimToken) {
        markAsPublished(eventId);
    }

    void markAsFailed(String eventId, @Nullable String errorMessage, int maxRetries, BackoffStrategy backoff);

    default void markAsFailed(String eventId, @Nullable String claimToken,
                              @Nullable String errorMessage, int maxRetries, BackoffStrategy backoff) {
        markAsFailed(eventId, errorMessage, maxRetries, backoff);
    }

    void reactivate(String eventId);

    /// Atomically claims dispatchable events for multi-instance safe dispatch.
    /// <p>
    /// Implementations must ensure that the same record is not returned to two
    /// concurrent claimers. The recommended implementation first pages
    /// dispatchable candidates, then performs a CAS update for each candidate:
    /// <pre>
    /// UPDATE jfoundry_outbox_event
    ///   SET status = 'DISPATCHING', claimed_at = CURRENT_TIMESTAMP,
    ///       claimed_by = #{claimerId}, claim_token = #{claimToken}
    ///   WHERE event_id = #{eventId}
    ///     AND status = #{candidateStatus};
    /// </pre>
    /// <p>
    /// Only records whose CAS update succeeds may appear in the return value.
    /// CAS failure means another claimer already claimed the record, so the
    /// implementation should skip it and keep filling the batch. Implementations
    /// may use native top-N update or locking syntax, but the exposed semantics
    /// must remain the same.
    /// <p>
    /// Each call should generate a unique {@code claimToken}, for example a UUID,
    /// and write it to {@code claim_token}. This helps diagnose the claim batch
    /// and lets {@link #markAsPublished(String, String)} /
    /// {@link #markAsFailed(String, String, String, int, BackoffStrategy)} verify
    /// that the caller still owns the record. The token should be cleared when
    /// leaving DISPATCHING.
    /// <p>
    /// Parameter constraints:
    /// <ul>
    ///   <li>{@code limit <= 0} throws {@link IllegalArgumentException}</li>
    ///   <li>null or blank {@code claimerId} throws {@link IllegalArgumentException}</li>
    /// </ul>
    List<OutboxMessage> claimDispatchable(int limit, String claimerId);

    /// Recovers stuck DISPATCHING records whose {@code claimedAt} is before {@code cutoff}.
    /// <p>
    /// Implementations typically use SQL similar to:
    /// <pre>
    /// UPDATE jfoundry_outbox_event
    ///   SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL, claim_token = NULL
    ///   WHERE status = 'DISPATCHING' AND claimed_at &lt; #{cutoff};
    /// </pre>
    /// <p>
    /// This covers pods that crash while records remain DISPATCHING. Call this
    /// periodically with {@code Instant.now().minus(stuckTimeout)} to recover
    /// those records.
    /// <p>
    /// Parameter constraints:
    /// <ul>
    ///   <li>null {@code cutoff} throws {@link IllegalArgumentException}</li>
    /// </ul>
    /// @param cutoff cutoff instant; DISPATCHING records claimed strictly before it are recovered
    /// @return number of recovered records, or 0 when no records are stuck
    int recoverStuckDispatching(Instant cutoff);

    /// Deletes terminal records in batches by status and occurrence time.
    /// <p>
    /// Implementations delete at most {@code batchSize} records per batch and
    /// loop until candidates are exhausted. They may use selectPage + deleteByIds
    /// or native batch-delete syntax. The return value is the accumulated number
    /// of deleted records.
    /// <p>
    /// This prevents PUBLISHED / DEAD_LETTERED records from accumulating and
    /// slowing claim/dispatch queries. Call it periodically with
    /// {@code Instant.now().minus(retentionDays)}. The operation is idempotent;
    /// repeated runs have no side effects, and failures do not affect the main
    /// Outbox path.
    /// <p>
    /// Parameter constraints:
    /// <ul>
    ///   <li>null {@code status} throws {@link IllegalArgumentException}</li>
    ///   <li>null {@code cutoff} throws {@link IllegalArgumentException}</li>
    ///   <li>{@code batchSize &lt;= 0} throws {@link IllegalArgumentException}</li>
    /// </ul>
    /// @param status terminal status, PUBLISHED or DEAD_LETTERED
    /// @param cutoff cutoff instant; records occurred strictly before it are deleted
    /// @param batchSize maximum number of records deleted per batch
    /// @return accumulated number of deleted records, or 0 when no records match
    int deleteByStatusAndOccurredAtBefore(OutboxMessageStatus status, Instant cutoff, int batchSize);
}
