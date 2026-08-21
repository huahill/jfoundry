package org.jfoundry.infrastructure.inbox.mybatis;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackages = "org.jfoundry.infrastructure.inbox.mybatis")
class InboxPersistenceTestConfig {

    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("jfoundry-inbox-test")
                .addScript("classpath:inbox_message.sql")
                .build();
    }

    @Bean
    MybatisPlusInboxMessageStore mybatisPlusInboxMessageStore(InboxMessageMapper mapper) {
        return new MybatisPlusInboxMessageStore(mapper);
    }
}
