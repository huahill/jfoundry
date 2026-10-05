package org.jfoundry.application.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExponentialBackoffStrategyTest {

    @Test
    void firstFailureUsesBaseDelay() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(strategy.nextDelay(0)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void subsequentFailuresDoubleDelay() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(strategy.nextDelay(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(strategy.nextDelay(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(strategy.nextDelay(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(strategy.nextDelay(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(strategy.nextDelay(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(strategy.nextDelay(5)).isEqualTo(Duration.ofSeconds(32));
    }

    @Test
    void delayCapsAtMax() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(strategy.nextDelay(6)).isEqualTo(Duration.ofMinutes(1));
        assertThat(strategy.nextDelay(100)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void negativeFailedAttemptsTreatedAsZero() {
        ExponentialBackoffStrategy strategy = new ExponentialBackoffStrategy(
                Duration.ofSeconds(1), Duration.ofMinutes(1));

        assertThat(strategy.nextDelay(-1)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void rejectsNonPositiveBase() {
        assertThatThrownBy(() -> new ExponentialBackoffStrategy(Duration.ZERO, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base must be positive");
    }
}
