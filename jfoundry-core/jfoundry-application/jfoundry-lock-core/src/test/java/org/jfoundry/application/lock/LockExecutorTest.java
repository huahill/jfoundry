package org.jfoundry.application.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockExecutorTest {

    @Test
    void executesCallbackWhenLockIsAcquiredAndReleasesAfterwards() throws Exception {
        RecordingLockClient client = new RecordingLockClient(true);
        LockExecutor executor = LockExecutor.create(client);
        LockKey key = new LockKey("order-processing", "customer:42");

        String result = executor.execute(key, LockOptions.defaults(), () -> "handled");

        assertThat(result).isEqualTo("handled");
        assertThat(client.events).containsExactly("try:order-processing", "release:order-processing");
    }

    @Test
    void releasesLockWhenCallbackFails() {
        RecordingLockClient client = new RecordingLockClient(true);
        LockExecutor executor = LockExecutor.create(client);
        LockKey key = new LockKey("order-processing", "customer:42");

        assertThatThrownBy(() -> executor.execute(key, LockOptions.defaults(), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(client.events).containsExactly("try:order-processing", "release:order-processing");
    }

    @Test
    void doesNotExposeLockValueWhenAcquisitionFails() {
        LockExecutor executor = LockExecutor.create(new RecordingLockClient(false));
        LockKey key = new LockKey("order-processing", "customer:42");
        LockOptions options = LockOptions.builder()
                .waitTime(Duration.ofMillis(10))
                .leaseTime(Duration.ofSeconds(5))
                .failureMode(LockFailureMode.THROW)
                .build();

        assertThatThrownBy(() -> executor.execute(key, options, () -> "ignored"))
                .isInstanceOf(DistributedLockUnavailableException.class)
                .hasMessageContaining("order-processing")
                .hasMessageNotContaining("customer:42");
    }

    @Test
    void skipsCallbackWhenLockCannotBeAcquiredAndFailureModeIsSkip() throws Exception {
        RecordingLockClient client = new RecordingLockClient(false);
        LockExecutor executor = LockExecutor.create(client);
        LockKey key = new LockKey("order-processing", "customer:42");

        String result = executor.execute(key, LockOptions.builder()
                .failureMode(LockFailureMode.SKIP)
                .build(), () -> "ignored");

        assertThat(result).isNull();
        assertThat(client.events).containsExactly("try:order-processing");
    }

    static class RecordingLockClient implements DistributedLockClient {

        private final boolean acquired;
        private final List<String> events = new ArrayList<>();

        RecordingLockClient(boolean acquired) {
            this.acquired = acquired;
        }

        @Override
        public LockHandle tryLock(LockKey key, LockOptions options) {
            events.add("try:" + key.scope());
            return new LockHandle(key, acquired, () -> events.add("release:" + key.scope()));
        }
    }
}
