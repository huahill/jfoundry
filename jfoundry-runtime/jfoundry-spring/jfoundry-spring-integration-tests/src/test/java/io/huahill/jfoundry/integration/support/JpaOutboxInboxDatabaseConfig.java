package io.huahill.jfoundry.integration.support;

import io.huahill.jfoundry.autoconfigure.inbox.InboxMybatisPlusAutoConfiguration;
import io.huahill.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        InboxMybatisPlusAutoConfiguration.class,
        OutboxMybatisPlusAutoConfiguration.class
})
public class JpaOutboxInboxDatabaseConfig {
}
