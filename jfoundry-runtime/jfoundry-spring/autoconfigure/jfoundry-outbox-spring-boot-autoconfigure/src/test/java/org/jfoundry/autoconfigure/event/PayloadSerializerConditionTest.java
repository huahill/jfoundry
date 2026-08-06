package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/// {@code payloadSerializer} must use {@code @ConditionalOnBean(ObjectMapper.class)}, so
/// applications without Jackson do not fail startup because an ObjectMapper bean is missing.
/// <p>
/// Also verifies the transitive guard: the new
/// {@code @ConditionalOnBean(PayloadSerializer.class)} on {@code domainEventOutboxRecorder} takes
/// effect, so the Outbox recorder backs off when no serializer exists instead of failing due to a
/// missing dependency.
class PayloadSerializerConditionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DomainEventOutboxRecorderAutoConfiguration.class))
                    .withBean(OutboxMessageStore.class, () -> mock(OutboxMessageStore.class));

    @Test
    void contextStartsWithoutFailureWhenObjectMapperBeanIsMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PayloadSerializer.class);
        });
    }

    @Test
    void outboxRecorderAlsoRetractsWhenPayloadSerializerMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(DomainEventOutboxRecorder.class);
        });
    }
}
