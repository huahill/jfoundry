package org.jfoundry.autoconfigure.persistence.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.jfoundry.autoconfigure.persistence.AuditStampingAutoConfiguration;
import org.jfoundry.infrastructure.persistence.AuditStamping;
import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAuditMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/// Registers the JFoundry MyBatis-Plus technical audit handler when no application handler exists.
@AutoConfiguration
@AutoConfigureAfter(AuditStampingAutoConfiguration.class)
@ConditionalOnClass({MetaObjectHandler.class, MybatisPlusAuditMetaObjectHandler.class})
@ConditionalOnBean(AuditStamping.class)
public class MybatisPlusAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    MybatisPlusAuditMetaObjectHandler mybatisPlusAuditMetaObjectHandler(AuditStamping auditStamping) {
        return new MybatisPlusAuditMetaObjectHandler(auditStamping);
    }
}
