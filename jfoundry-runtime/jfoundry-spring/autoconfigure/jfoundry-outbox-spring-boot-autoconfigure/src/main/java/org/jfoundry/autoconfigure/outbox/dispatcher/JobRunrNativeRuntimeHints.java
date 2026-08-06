package org.jfoundry.autoconfigure.outbox.dispatcher;

import org.jfoundry.infrastructure.outbox.jobrunr.dispatcher.OutboxDispatchJobRequest;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class JobRunrNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("org/jobrunr/storage/sql/**");
        hints.reflection().registerType(OutboxDispatchJobRequest.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
