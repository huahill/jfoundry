package org.jfoundry.infrastructure.outbox.mybatis;

import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// claimDispatchable must be atomic across threads: no two claimers receive the same record.
@SpringBootTest(classes = ClaimDispatchableConcurrencyTest.TestApp.class)
class ClaimDispatchableConcurrencyTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp extends OutboxPersistenceTestConfig {
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
    void twoConcurrentClaimersGetDisjointRecords() throws Exception {
        // Seed 20 PENDING records
        for (int i = 0; i < 20; i++) {
            OutboxMessage e = OutboxMessage.newPending(
                    "evt-" + i, "test.event", null, "test.type", "{}", Instant.now());
            repository.append(e);
        }

        Set<String> claimedByA = ConcurrentHashMap.newKeySet();
        Set<String> claimedByB = ConcurrentHashMap.newKeySet();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        pool.submit(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            repository.claimDispatchable(10, "pod-A").forEach(e -> claimedByA.add(e.getEventId()));
            done.countDown();
        });
        pool.submit(() -> {
            try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            repository.claimDispatchable(10, "pod-B").forEach(e -> claimedByB.add(e.getEventId()));
            done.countDown();
        });

        start.countDown();
        done.await();
        pool.shutdown();

        // Assertions
        assertThat(claimedByA).hasSize(10);
        assertThat(claimedByB).hasSize(10);

        Set<String> intersection = new HashSet<>(claimedByA);
        intersection.retainAll(claimedByB);
        assertThat(intersection)
                .as("claimDispatchable must be atomic: zero intersection between two claimers")
                .isEmpty();
    }

    @Test
    void claimDispatchableOnlyTakesPendingRecords() {
        // Seed: 3 PENDING, 1 DISPATCHING, 1 PUBLISHED
        repository.append(pendingMessage("pending-1"));
        repository.append(pendingMessage("pending-2"));
        repository.append(pendingMessage("pending-3"));

        OutboxMessage dispatching = pendingMessage("dispatching-1");
        dispatching.setStatus(OutboxMessageStatus.DISPATCHING);
        repository.append(dispatching);

        OutboxMessage published = pendingMessage("published-1");
        published.setStatus(OutboxMessageStatus.PUBLISHED);
        repository.append(published);

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");

        assertThat(claimed).extracting(OutboxMessage::getEventId)
                .containsExactlyInAnyOrder("pending-1", "pending-2", "pending-3");
    }

    /// Claim must include retry-due FAILED rows, matching {@code findDispatchable}
    /// candidate semantics. Otherwise, failed retries starve after switching to claim mode.
    @Test
    void claimDispatchableAlsoTakesRetryDueFailed() {
        repository.append(pendingMessage("pending-1"));

        OutboxMessage failedDue = pendingMessage("failed-due-1");
        failedDue.setStatus(OutboxMessageStatus.FAILED);
        failedDue.setNextRetryAt(Instant.now().minusSeconds(60));
        repository.append(failedDue);

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");

        assertThat(claimed).extracting(OutboxMessage::getEventId)
                .containsExactlyInAnyOrder("pending-1", "failed-due-1");
    }

    @Test
    void claimDispatchableSkipsFailedNotYetDue() {
        OutboxMessage failedFuture = pendingMessage("failed-future-1");
        failedFuture.setStatus(OutboxMessageStatus.FAILED);
        failedFuture.setNextRetryAt(Instant.now().plusSeconds(600));
        repository.append(failedFuture);

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");

        assertThat(claimed).isEmpty();
    }

    @Test
    void claimDispatchableSkipsDeadLettered() {
        OutboxMessage deadLettered = pendingMessage("dead-1");
        deadLettered.setStatus(OutboxMessageStatus.DEAD_LETTERED);
        repository.append(deadLettered);

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");

        assertThat(claimed).isEmpty();
    }

    @Test
    void claimDispatchableUpdatesStatusToDispatching() {
        repository.append(pendingMessage("evt-1"));

        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxMessageStatus.DISPATCHING);
        assertThat(claimed.get(0).getClaimedBy()).isEqualTo("pod-X");
        assertThat(claimed.get(0).getClaimedAt()).isNotNull();
    }

    @Test
    void claimDispatchableReturnsEmptyWhenNoPending() {
        List<OutboxMessage> claimed = repository.claimDispatchable(10, "pod-X");
        assertThat(claimed).isEmpty();
    }

    @Test
    void claimDispatchableRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> repository.claimDispatchable(0, "pod-X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> repository.claimDispatchable(-1, "pod-X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void claimDispatchableRejectsBlankClaimerId() {
        assertThatThrownBy(() -> repository.claimDispatchable(10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimerId");
        assertThatThrownBy(() -> repository.claimDispatchable(10, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimerId");
    }

    private OutboxMessage pendingMessage(String eventId) {
        return OutboxMessage.newPending(
                eventId, "topic", null, "com.example.Foo", "{}", Instant.now());
    }

    /// When the same pod re-enters dispatch, readback must match the claimToken
    /// generated for the current call. It must not include DISPATCHING stragglers from a previous
    /// batch caused by {@code markAsPublished}/{@code markAsFailed} status update failures or pod
    /// re-entry during the send loop, because that would send them twice.
    /// <p>
    /// The previous implementation, which read back by stable {@code claimed_by + DISPATCHING},
    /// would re-read R1/R2 from the first batch during the second claim, causing duplicate sends.
    /// After the fix, the two batches' eventId sets are strictly disjoint.
    @Test
    void reentrantClaimOnSamePodDoesNotReReadPriorBatchStragglers() {
        // Seed R1, R2 as batch 1.
        repository.append(pendingMessage("batch1-1"));
        repository.append(pendingMessage("batch1-2"));
        List<OutboxMessage> batch1 = repository.claimDispatchable(2, "pod-A");
        assertThat(batch1).extracting(OutboxMessage::getEventId)
                .containsExactlyInAnyOrder("batch1-1", "batch1-2");

        // Simulate status update failure: the two batch1 records remain DISPATCHING because
        // markAsPublished/markAsFailed was not called or rolled back, and claimed_by is still
        // "pod-A". At this point, pod A re-enters dispatch in the same thread and claims a new batch.
        repository.append(pendingMessage("batch2-1"));
        repository.append(pendingMessage("batch2-2"));
        List<OutboxMessage> batch2 = repository.claimDispatchable(2, "pod-A");

        // Key assertion: batch2 can only contain batch2-1 / batch2-2 and must not re-read batch1 rows.
        assertThat(batch2).extracting(OutboxMessage::getEventId)
                .containsExactlyInAnyOrder("batch2-1", "batch2-2");

        // Mutual-exclusion guard: the two batches' eventIds must be strictly disjoint.
        Set<String> batch1Ids = new HashSet<>(batch1.stream().map(OutboxMessage::getEventId).toList());
        Set<String> batch2Ids = new HashSet<>(batch2.stream().map(OutboxMessage::getEventId).toList());
        Set<String> intersection = new HashSet<>(batch1Ids);
        intersection.retainAll(batch2Ids);
        assertThat(intersection)
                .as("same-pod reentrant dispatch must not read back prior DISPATCHING stragglers")
                .isEmpty();
    }
}
