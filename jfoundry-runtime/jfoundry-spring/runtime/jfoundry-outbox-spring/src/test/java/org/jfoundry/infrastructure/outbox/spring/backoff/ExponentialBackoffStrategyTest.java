package org.jfoundry.infrastructure.outbox.spring.backoff;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffStrategyTest {

    @Test
    void firstFailureUsesBaseDelay() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(1000L, 60_000L);

        assertThat(strategy.nextDelay(0)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void subsequentFailuresDoubleDelay() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(1000L, 60_000L);

        assertThat(strategy.nextDelay(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(strategy.nextDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(strategy.nextDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(strategy.nextDelay(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(strategy.nextDelay(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(strategy.nextDelay(5)).isEqualTo(Duration.ofSeconds(32));
    }

    @Test
    void delayCapsAtMax() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(1000L, 60_000L);

        assertThat(strategy.nextDelay(6)).isEqualTo(Duration.ofMinutes(1));
        assertThat(strategy.nextDelay(100)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void negativeFailedAttemptsTreatedAsZero() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(1000L, 60_000L);

        assertThat(strategy.nextDelay(-1)).isEqualTo(Duration.ofSeconds(1));
    }
}
