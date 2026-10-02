package org.jfoundry.application.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockDurationsTest {

    @Test
    void parsesRequiredDurationsAndTreatsBlankLeaseAsBackendDefault() {
        assertThat(LockDurations.required("2s", "waitTime")).isEqualTo(Duration.ofSeconds(2));
        assertThat(LockDurations.optional("5s", "leaseTime")).contains(Duration.ofSeconds(5));
        assertThat(LockDurations.optional("  ", "leaseTime")).isEmpty();
    }

    @Test
    void rejectsBlankRequiredDuration() {
        assertThatThrownBy(() -> LockDurations.required(" ", "waitTime"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("waitTime must not be blank");
    }
}
