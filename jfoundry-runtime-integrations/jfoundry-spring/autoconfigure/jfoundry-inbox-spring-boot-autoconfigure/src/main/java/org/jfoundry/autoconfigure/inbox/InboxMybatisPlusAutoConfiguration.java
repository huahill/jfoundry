package org.jfoundry.autoconfigure.inbox;

import org.jfoundry.application.inbox.InboxMessageStore;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@MapperScan(basePackages = "org.jfoundry.infrastructure.inbox.mybatis")
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnClass({SqlSessionFactory.class, MapperScan.class, MybatisPlusInboxMessageStore.class})
public class InboxMybatisPlusAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(InboxMessageStore.class)
    public InboxMessageStore inboxMessageStore(InboxMessageMapper mapper) {
        return new MybatisPlusInboxMessageStore(mapper);
    }
}
