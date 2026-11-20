package io.huahill.jfoundry.infrastructure.outbox.quarkus.externalization;

import io.huahill.jfoundry.application.event.BeforeCommitDomainEventDispatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDomainEventDispatcherTest {

    @Test
    void declaresThatItMustRunBeforeTransactionCommit() {
        assertThat(BeforeCommitDomainEventDispatcher.class)
                .isAssignableFrom(OutboxDomainEventDispatcher.class);
    }
}
