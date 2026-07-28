package org.jfoundry.autoconfigure.outbox.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.jfoundry.autoconfigure.outbox.JfoundryOutboxProperties;
import org.jfoundry.application.outbox.OutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.MybatisPlusOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Default MyBatis-Plus bean auto-configuration:
/// <ul>
///   <li>{@code @MapperScan} explicitly scans the OutboxMapper package. ImportBeanDefinitionRegistrar
///       registers bean definitions during ConfigurationClassParser processing so downstream
///       AutoConfiguration classes can resolve them through {@code @Autowired} injection.</li>
///   <li>Provides a default MybatisPlusInterceptor containing only PaginationInnerInterceptor when
///       no MybatisPlusInterceptor has been registered.</li>
///   <li>Registers MybatisPlusOutboxMessageStore as the default OutboxMessageStore implementation.</li>
///   <li>Appends an Outbox dynamic table-name inner interceptor to MybatisPlusInterceptor,
///       rewriting the OutboxData logical table name {@code jfoundry_outbox_event} to the configured
///       {@code jfoundry.outbox.table-name}.</li>
///   <li>Leaves pagination dialect detection to MyBatis-Plus to avoid duplicating database-type
///       configuration.</li>
/// </ul>
/// <p>
/// Note: {@code mybatisPlusOutboxMessageStore} deliberately does not use
/// {@code @ConditionalOnBean(OutboxMapper.class)} because MapperScannerConfigurer is a
/// BeanDefinitionRegistryPostProcessor and its registration timing is later than
/// {@code @ConditionalOnBean} evaluation. OutboxMapper is injected through the constructor; if the
/// mapper is missing, bean creation fails explicitly.
@AutoConfiguration
@AutoConfigureAfter(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@MapperScan(basePackages = "org.jfoundry.infrastructure.outbox.mybatis")
@ConditionalOnClass({MybatisPlusInterceptor.class, MapperScan.class, MybatisPlusOutboxMessageStore.class})
@EnableConfigurationProperties(JfoundryOutboxProperties.class)
public class OutboxMybatisPlusAutoConfiguration {

    @Bean
    public OutboxTableNameCustomizer outboxTableNameCustomizer(JfoundryOutboxProperties properties) {
        return new OutboxTableNameCustomizer(properties);
    }

    @Bean
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            OutboxTableNameCustomizer outboxTableNameCustomizer) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // Table-name rewriting must run before pagination because pagination adds LIMIT to the
        // rewritten SQL.
        outboxTableNameCustomizer.customize(interceptor);
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public SmartInitializingSingleton outboxTableNameInterceptorInitializer(
            ObjectProvider<MybatisPlusInterceptor> interceptors,
            OutboxTableNameCustomizer outboxTableNameCustomizer) {
        return () -> interceptors.orderedStream()
                .forEach(outboxTableNameCustomizer::customize);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxMessageStore.class)
    public OutboxMessageStore mybatisPlusOutboxMessageStore(
            OutboxMapper outboxMapper,
            MybatisPlusInterceptor mybatisPlusInterceptor) {
        return new MybatisPlusOutboxMessageStore(outboxMapper, mybatisPlusInterceptor);
    }
}
