package org.jfoundry.integration.support;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.MybatisPlusOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = {OutboxMapper.class, InboxMessageMapper.class})
public class PostgreSqlOutboxInboxDatabaseConfig {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    MybatisPlusOutboxMessageStore mybatisPlusOutboxMessageStore(OutboxMapper outboxMapper,
                                                               MybatisPlusInterceptor mybatisPlusInterceptor) {
        return new MybatisPlusOutboxMessageStore(outboxMapper, mybatisPlusInterceptor);
    }

    @Bean
    MybatisPlusInboxMessageStore mybatisPlusInboxMessageStore(InboxMessageMapper inboxMessageMapper) {
        return new MybatisPlusInboxMessageStore(inboxMessageMapper);
    }
}
