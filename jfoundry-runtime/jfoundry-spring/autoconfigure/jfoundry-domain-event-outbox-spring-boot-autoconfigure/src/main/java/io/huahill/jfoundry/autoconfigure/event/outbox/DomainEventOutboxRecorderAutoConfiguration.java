package io.huahill.jfoundry.autoconfigure.event.outbox;

import io.huahill.jfoundry.application.event.externalization.AggregateRoutingResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizationResolver;
import io.huahill.jfoundry.application.event.externalization.DomainEventExternalizer;
import io.huahill.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import io.huahill.jfoundry.application.event.outbox.DefaultDomainEventOutboxRecorder;
import io.huahill.jfoundry.application.event.outbox.DomainEventOutboxRecorder;
import io.huahill.jfoundry.application.messaging.PayloadSerializer;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.infrastructure.outbox.spring.externalization.OutboxDomainEventDispatcher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.List;

/// Auto-configuration for the explicit Domain Event to Outbox composition.
@AutoConfiguration
@AutoConfigureAfter(name = {
        "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration",
        "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxJpaAutoConfiguration",
        "io.huahill.jfoundry.autoconfigure.outbox.OutboxTemplateAutoConfiguration"
})
@AutoConfigureBefore(name = "io.huahill.jfoundry.autoconfigure.event.DomainEventDispatchAutoConfiguration")
@ConditionalOnClass({DomainEventOutboxRecorder.class, OutboxMessageStore.class, PayloadSerializer.class,
        OutboxDomainEventDispatcher.class})
@EnableConfigurationProperties(DomainEventOutboxProperties.class)
public class DomainEventOutboxRecorderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ExternalizationRuleResolver.class)
    public ExternalizationRuleResolver externalizationRuleResolver() {
        return new ExternalizationRuleResolver();
    }

    @Bean
    @ConditionalOnMissingBean(AggregateRoutingResolver.class)
    public AggregateRoutingResolver aggregateRoutingResolver() {
        return new AggregateRoutingResolver();
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, PayloadSerializer.class})
    @ConditionalOnMissingBean(DomainEventOutboxRecorder.class)
    public DefaultDomainEventOutboxRecorder domainEventOutboxRecorder(
            OutboxMessageStore outboxRepository,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver,
            PayloadSerializer serializer,
            List<DomainEventExternalizer<?>> externalizers) {
        return new DefaultDomainEventOutboxRecorder(
                outboxRepository,
                serializer,
                ruleResolver,
                aggregateRoutingResolver,
                new DomainEventExternalizationResolver(externalizers));
    }

    @Bean
    @ConditionalOnBean(DomainEventOutboxRecorder.class)
    @ConditionalOnMissingBean(OutboxDomainEventDispatcher.class)
    @Conditional(DomainEventOutboxEnabled.class)
    public OutboxDomainEventDispatcher outboxDomainEventDispatcher(
            DomainEventOutboxRecorder recorder) {
        return new OutboxDomainEventDispatcher(() -> recorder);
    }

    private static final class DomainEventOutboxEnabled implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(org.springframework.context.annotation.ConditionContext context,
                               org.springframework.core.type.AnnotatedTypeMetadata metadata) {
            return Boolean.parseBoolean(context.getEnvironment().getProperty(
                    "jfoundry.domain.event.dispatch.outbox.enabled", "false"));
        }
    }
}
