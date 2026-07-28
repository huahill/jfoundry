package org.jfoundry.autoconfigure.outbox.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jfoundry.application.outbox.OutboxMessage;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = OutboxTableNameWithCustomInterceptorTest.TestApp.class,
        properties = {
                "jfoundry.outbox.table-name=custom_outbox",
                "jfoundry.outbox.dispatcher.mode=none",
                "spring.autoconfigure.exclude=org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration",
                "spring.datasource.url=jdbc:h2:mem:jfoundry-custom-interceptor;DB_CLOSE_DELAY=-1",
                "spring.sql.init.schema-locations=classpath:outbox_event.sql"
        }
)
@Sql(scripts = "classpath:outbox_custom_table.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OutboxTableNameWithCustomInterceptorTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class TestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
            return interceptor;
        }
    }

    @Autowired
    private OutboxMessageStore repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM custom_outbox");
        jdbc.update("DELETE FROM jfoundry_outbox_event");
    }

    @Test
    void customInterceptorStillReceivesOutboxTableRewrite() {
        OutboxMessage entry = OutboxMessage.newPending(
                "evt-custom-interceptor", "test.event", null, "test.type", "{}", Instant.now());

        repository.append(entry);

        Integer customCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM custom_outbox WHERE event_id = ?",
                Integer.class, "evt-custom-interceptor");
        Integer defaultCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jfoundry_outbox_event WHERE event_id = ?",
                Integer.class, "evt-custom-interceptor");

        assertThat(customCount).isEqualTo(1);
        assertThat(defaultCount).isEqualTo(0);
    }
}
