package org.jfoundry.autoconfigure.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// JFoundry does not expose its own database dialect setting. The default
/// {@link PaginationInnerInterceptor} keeps {@code dbType} as {@code null}, so
/// MyBatis-Plus auto-detects the dialect lazily on the first paging query.
@SpringBootTest(classes = PaginationInterceptorDbTypeDefaultTest.NoDbTypeTestApp.class)
@TestPropertySource(properties = {
        "jfoundry.outbox.dispatcher.mode=none",
        "spring.autoconfigure.exclude=org.jfoundry.autoconfigure.outbox.dispatcher.OutboxDispatcherAutoConfiguration",
        "spring.datasource.url=jdbc:h2:mem:jfoundry-db-type-default;DB_CLOSE_DELAY=-1",
        "spring.sql.init.schema-locations=classpath:outbox_event.sql"
})
class PaginationInterceptorDbTypeDefaultTest {

    @Autowired
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    @Test
    void paginationInnerInterceptorUsesMpDefaultAutoDetection() {
        PaginationInnerInterceptor pagination = findPaginationInnerInterceptor(mybatisPlusInterceptor);

        assertThat(pagination.getDbType())
                .as("JFoundry must not set PaginationInnerInterceptor.dbType; MyBatis-Plus auto-detects it")
                .isNull();
    }

    private static PaginationInnerInterceptor findPaginationInnerInterceptor(MybatisPlusInterceptor interceptor) {
        List<InnerInterceptor> inners = interceptor.getInterceptors();
        return inners.stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "PaginationInnerInterceptor not registered in MybatisPlusInterceptor; got: " + inners));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
    static class NoDbTypeTestApp {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
