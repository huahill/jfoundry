package org.jfoundry.autoconfigure.event;

import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.autoconfigure.outbox.OutboxTemplateAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/// {@code payloadSerializer} must use {@code @ConditionalOnBean(ObjectMapper.class)}, so
/// applications without Jackson do not fail startup because an ObjectMapper bean is missing.
/// <p>
/// Also verifies the transitive guard on {@code outboxTemplate}, so the generic template backs off
/// when no serializer exists instead of failing due to a missing dependency.
class PayloadSerializerConditionTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(OutboxTemplateAutoConfiguration.class))
                    .withBean(OutboxMessageStore.class, () -> mock(OutboxMessageStore.class));

    @Test
    void contextStartsWithoutFailureWhenObjectMapperBeanIsMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PayloadSerializer.class);
        });
    }

    @Test
    void outboxTemplateAlsoRetractsWhenPayloadSerializerMissing() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OutboxTemplate.class);
        });
    }
}
