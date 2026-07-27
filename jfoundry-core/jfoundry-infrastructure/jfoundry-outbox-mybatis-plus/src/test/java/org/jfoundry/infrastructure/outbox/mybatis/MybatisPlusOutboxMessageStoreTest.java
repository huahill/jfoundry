package org.jfoundry.infrastructure.outbox.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.jfoundry.application.messaging.MessagePropagation;
import org.jfoundry.application.outbox.BackoffStrategy;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = OutboxPersistenceTestConfig.class)
class MybatisPlusOutboxMessageStoreTest {

    @Autowired
    private MybatisPlusOutboxMessageStore repository;

    private final BackoffStrategy fixedBackoff = failedAttempts -> Duration.ofSeconds(10);

    @BeforeEach
    void cleanDb(@Autowired OutboxMapper mapper) {
        mapper.delete(null);
    }

    @Test
    void appendPersistsEntry() {
        OutboxMessage entry = pendingMessage("evt-1");
        repository.append(entry);

        OutboxMessage loaded = repository.findDispatchable(100, Instant.now()).get(0);
        assertThat(loaded.getEventId()).isEqualTo("evt-1");
    }

    @Test
    void appendPersistsAggregateRoutingMetadata() {
        OutboxMessage entry = OutboxMessage.newPending(
                "evt-aggregate", "topic", null, "com.example.Foo", "{}", Instant.now(),
                "Order", "order-1", 7L);

        repository.append(entry);

        OutboxMessage loaded = repository.findDispatchable(100, Instant.now()).get(0);
        assertThat(loaded.getAggregateType()).isEqualTo("Order");
        assertThat(loaded.getAggregateId()).isEqualTo("order-1");
        assertThat(loaded.getAggregateVersion()).isEqualTo(7L);
    }

    @Test
    void appendPersistsTraceContextPropagation() {
        OutboxMessage entry = pendingMessage("evt-trace-context");
        entry.setPropagation(MessagePropagation.from(Map.of(
                "traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "tracestate", "vendor=value")));

        repository.append(entry);

        OutboxMessage loaded = repository.findDispatchable(100, Instant.now()).getFirst();
        assertThat(loaded.getPropagation()).isEqualTo(entry.getPropagation());
    }

    @Test
    void findDispatchableReturnsOnlyPendingOrFailedReady() {
        repository.append(pendingMessage("evt-ready"));
        OutboxMessage failedNotReady = pendingMessage("evt-failed-not-ready");
        failedNotReady.markFailed("err", 5, fixedBackoff); // nextRetryAt = now + 10s
        repository.append(failedNotReady);

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());

        assertThat(ready).extracting(OutboxMessage::getEventId).containsExactly("evt-ready");
    }

    @Test
    void findDispatchableReturnsFailedWhoseNextRetryAtReached() throws InterruptedException {
        BackoffStrategy instant = failedAttempts -> Duration.ofMillis(1);
        OutboxMessage failed = pendingMessage("evt-failed");
        failed.markFailed("err", 5, instant);
        repository.append(failed);

        Thread.sleep(20); // wait for next_retry_at to pass

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).extracting(OutboxMessage::getEventId).contains("evt-failed");
    }

    @Test
    void markAsPublishedTransitionsToPublished() {
        repository.append(pendingMessage("evt-1"));
        repository.claimDispatchable(1, "pod-a");
        repository.markAsPublished("evt-1");

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).isEmpty();
    }

    @Test
    void markAsPublishedIgnoresUnclaimedMessage() {
        repository.append(pendingMessage("evt-1"));

        repository.markAsPublished("evt-1");

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).extracting(OutboxMessage::getEventId).containsExactly("evt-1");
    }

    @Test
    void markAsPublishedRejectsStaleClaimToken() {
        repository.append(pendingMessage("evt-1"));
        OutboxMessage firstClaim = repository.claimDispatchable(1, "pod-a").get(0);
        repository.recoverStuckDispatching(Instant.now().plusSeconds(1));
        OutboxMessage secondClaim = repository.claimDispatchable(1, "pod-b").get(0);

        repository.markAsPublished("evt-1", firstClaim.getClaimToken());

        OutboxData data = mapper().selectById("evt-1");
        assertThat(data.getStatus()).isEqualTo("DISPATCHING");
        assertThat(data.getClaimToken()).isEqualTo(secondClaim.getClaimToken());
    }

    @Test
    void markAsFailedIncrementsRetryAndSetsNextRetryAt() {
        repository.append(pendingMessage("evt-1"));
        repository.claimDispatchable(1, "pod-a");

        repository.markAsFailed("evt-1", "boom", 5, fixedBackoff);

        // Should not be dispatchable immediately (nextRetryAt = now + 10s)
        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).isEmpty();
    }

    @Test
    void markAsFailedIgnoresUnclaimedMessage() {
        repository.append(pendingMessage("evt-1"));

        repository.markAsFailed("evt-1", "boom", 5, fixedBackoff);

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).extracting(OutboxMessage::getEventId).containsExactly("evt-1");
    }

    @Test
    void markAsFailedRejectsStaleClaimToken() {
        repository.append(pendingMessage("evt-1"));
        OutboxMessage firstClaim = repository.claimDispatchable(1, "pod-a").get(0);
        repository.recoverStuckDispatching(Instant.now().plusSeconds(1));
        OutboxMessage secondClaim = repository.claimDispatchable(1, "pod-b").get(0);

        repository.markAsFailed("evt-1", firstClaim.getClaimToken(), "boom", 5, fixedBackoff);

        OutboxData data = mapper().selectById("evt-1");
        assertThat(data.getStatus()).isEqualTo("DISPATCHING");
        assertThat(data.getClaimToken()).isEqualTo(secondClaim.getClaimToken());
        assertThat(data.getErrorMessage()).isNull();
    }

    @Test
    void markAsFailedTransitionsToDeadLetteredAtMaxRetries() {
        repository.append(pendingMessage("evt-1"));
        repository.claimDispatchable(1, "pod-a");

        repository.markAsFailed("evt-1", "boom", 1, fixedBackoff);

        // DEAD_LETTERED should not be dispatchable
        assertThat(repository.findDispatchable(100, Instant.now())).isEmpty();
    }

    @Test
    void reactivateResetsDeadLetteredToPending() {
        repository.append(pendingMessage("evt-1"));
        repository.claimDispatchable(1, "pod-a");
        repository.markAsFailed("evt-1", "boom", 1, fixedBackoff);

        repository.reactivate("evt-1");

        List<OutboxMessage> ready = repository.findDispatchable(100, Instant.now());
        assertThat(ready).extracting(OutboxMessage::getEventId).contains("evt-1");
    }

    @Test
    void reactivateRejectsNonDeadLettered() {
        repository.append(pendingMessage("evt-1"));

        assertThatThrownBy(() -> repository.reactivate("evt-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEAD_LETTERED");
    }

    @Test
    void markAsPublishedMissingEntryIsSilent() {
        // No exception thrown
        repository.markAsPublished("does-not-exist");
    }

    private OutboxMessage pendingMessage(String eventId) {
        return OutboxMessage.newPending(
                eventId, "topic", null, "com.example.Foo", "{}", Instant.now());
    }

    @Autowired
    private OutboxMapper mapper;

    private OutboxMapper mapper() {
        return mapper;
    }
}
