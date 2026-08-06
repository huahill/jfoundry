package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.infrastructure.outbox.spring.dispatcher.ScheduledOutboxDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledOutboxNativeRuntimeHintsTest {

    @Test
    void registersScheduledMethodsForNativeInvocation() {
        RuntimeHints hints = new RuntimeHints();

        new ScheduledOutboxNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ScheduledOutboxDispatcher.class, "scheduledDispatch")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(OutboxRecoveryJob.class, "recoverStuckDispatching")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(OutboxCleanupJob.class, "runOnce")
                .test(hints)).isTrue();
    }
}
