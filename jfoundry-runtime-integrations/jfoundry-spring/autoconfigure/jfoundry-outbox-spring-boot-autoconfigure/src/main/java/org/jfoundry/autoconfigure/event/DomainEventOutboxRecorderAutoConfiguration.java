package org.jfoundry.autoconfigure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.messaging.PayloadSerializer;
import org.jfoundry.application.event.externalization.AggregateRoutingResolver;
import org.jfoundry.application.event.externalization.ExternalizationRuleResolver;
import org.jfoundry.application.outbox.DomainEventOutboxRecorder;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.application.outbox.OutboxTemplate;
import org.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer;
import org.jfoundry.infrastructure.outbox.spring.externalization.DefaultDomainEventOutboxRecorder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// Auto-configuration for automatic domain-event and explicit Outbox recording.
/// <p>
/// Enables the default DomainEventOutboxRecorder when the application provides an OutboxMessageStore
/// bean. The default PayloadSerializer uses Jackson; applications can override it by registering
/// their own {@link PayloadSerializer} bean.
/// <p>
/// {@code payloadSerializer} is registered only when Jackson is on the classpath, avoiding startup
/// failures caused by a missing ObjectMapper when an application does not use Jackson.
/// <p>
/// {@code domainEventOutboxRecorder} requires both OutboxMessageStore and PayloadSerializer. When
/// PayloadSerializer is missing, for example because the application has no Jackson and no custom
/// serializer, the Outbox recorder backs off instead of failing the application context. Applications
/// without Jackson must register their own {@link PayloadSerializer} to enable Outbox writes.
/// <p>
/// {@code domainEventOutboxRecorder} is registered only when the application has not provided its own
/// {@link DomainEventOutboxRecorder}.
/// <p>
/// {@link OutboxTemplate} is registered from the same store and serializer dependencies. It lets
/// applications record an explicitly translated integration event without exposing the internal
/// domain event as the broker contract.
@AutoConfiguration
@AutoConfigureAfter(
        value = JacksonAutoConfiguration.class,
        name = {
                "org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration",
                "org.jfoundry.autoconfigure.outbox.persistence.OutboxJpaAutoConfiguration"
        })
@ConditionalOnClass({PayloadSerializer.class, JacksonPayloadSerializer.class, OutboxMessageStore.class, DefaultDomainEventOutboxRecorder.class})
public class DomainEventOutboxRecorderAutoConfiguration {

    /// Must be declared before {@link #domainEventOutboxRecorder}: {@code @ConditionalOnBean} is
    /// evaluated in declaration order during bean-definition registration, so downstream beans must
    /// be declared after their dependencies. Otherwise, the upstream bean may not be registered yet
    /// and the condition can incorrectly evaluate to false.
    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean(PayloadSerializer.class)
    public PayloadSerializer payloadSerializer(ObjectMapper objectMapper) {
        return new JacksonPayloadSerializer(objectMapper);
    }

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
    @ConditionalOnMissingBean(OutboxTemplate.class)
    public OutboxTemplate outboxTemplate(
            OutboxMessageStore store, PayloadSerializer serializer) {
        return new OutboxTemplate(store, serializer);
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, PayloadSerializer.class})
    @ConditionalOnMissingBean(DomainEventOutboxRecorder.class)
    public DefaultDomainEventOutboxRecorder domainEventOutboxRecorder(
            OutboxMessageStore outboxRepository,
            ExternalizationRuleResolver ruleResolver,
            AggregateRoutingResolver aggregateRoutingResolver,
            PayloadSerializer serializer) {
        return new DefaultDomainEventOutboxRecorder(outboxRepository, serializer, ruleResolver, aggregateRoutingResolver);
    }
}
