package io.huahill.jfoundry.autoconfigure.outbox;

import io.huahill.jfoundry.application.messaging.PayloadSerializer;
import io.huahill.jfoundry.application.outbox.OutboxMessageStore;
import io.huahill.jfoundry.application.outbox.OutboxTemplate;
import io.huahill.jfoundry.infrastructure.messaging.jackson.JacksonPayloadSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/// Auto-configuration for the generic Outbox recording template and payload serializer.
@AutoConfiguration
@AutoConfigureAfter(name = {
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration",
        "io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxJpaAutoConfiguration"
})
@ConditionalOnClass({OutboxTemplate.class, OutboxMessageStore.class, PayloadSerializer.class,
        JacksonPayloadSerializer.class})
public class OutboxTemplateAutoConfiguration {

    /// Provides the framework default only when the application uses Jackson and has not supplied
    /// another serializer.
    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnMissingBean(PayloadSerializer.class)
    public PayloadSerializer payloadSerializer(ObjectMapper objectMapper) {
        return new JacksonPayloadSerializer(objectMapper);
    }

    @Bean
    @ConditionalOnBean({OutboxMessageStore.class, PayloadSerializer.class})
    @ConditionalOnMissingBean(OutboxTemplate.class)
    public OutboxTemplate outboxTemplate(OutboxMessageStore store, PayloadSerializer serializer) {
        return new OutboxTemplate(store, serializer);
    }
}
