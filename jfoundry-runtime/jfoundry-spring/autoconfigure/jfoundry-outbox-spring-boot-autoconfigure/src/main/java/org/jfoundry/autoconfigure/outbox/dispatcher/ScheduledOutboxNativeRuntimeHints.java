package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.infrastructure.outbox.spring.dispatcher.ScheduledOutboxDispatcher;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class ScheduledOutboxNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(ScheduledOutboxDispatcher.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(OutboxRecoveryJob.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(OutboxCleanupJob.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
