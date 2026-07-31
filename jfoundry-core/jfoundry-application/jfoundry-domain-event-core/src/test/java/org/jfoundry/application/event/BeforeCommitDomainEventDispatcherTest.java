package org.jfoundry.application.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeforeCommitDomainEventDispatcherTest {

    @Test
    void marksDispatchersThatMustRunBeforeTheTransactionCommits() throws ClassNotFoundException {
        Class<?> type = Class.forName("org.jfoundry.application.event.BeforeCommitDomainEventDispatcher");

        assertThat(DomainEventDispatcher.class).isAssignableFrom(type);
    }
}
