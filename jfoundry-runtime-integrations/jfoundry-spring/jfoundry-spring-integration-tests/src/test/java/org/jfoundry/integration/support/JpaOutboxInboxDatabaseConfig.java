package org.jfoundry.integration.support;

import org.jfoundry.autoconfigure.inbox.InboxMybatisPlusAutoConfiguration;
import org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        InboxMybatisPlusAutoConfiguration.class,
        OutboxMybatisPlusAutoConfiguration.class
})
public class JpaOutboxInboxDatabaseConfig {
}
