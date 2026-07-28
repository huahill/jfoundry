package org.jfoundry.autoconfigure.persistence.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.jfoundry.autoconfigure.persistence.AuditStampingAutoConfiguration;
import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAuditMetaObjectHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AuditStampingAutoConfiguration.class,
                    MybatisPlusAuditAutoConfiguration.class));

    @Test
    void registersTheJFoundryAuditHandlerByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(MetaObjectHandler.class))
                .isInstanceOf(MybatisPlusAuditMetaObjectHandler.class));
    }

    @Test
    void backsOffWhenApplicationProvidesMetaObjectHandler() {
        MetaObjectHandler custom = new NoOpMetaObjectHandler();

        contextRunner.withBean(MetaObjectHandler.class, () -> custom)
                .run(context -> assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(custom));
    }

    @Test
    void doesNotRegisterWithoutAuditStamping() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MybatisPlusAuditAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(MetaObjectHandler.class));
    }

    private static final class NoOpMetaObjectHandler implements MetaObjectHandler {

        @Override
        public void insertFill(MetaObject metaObject) {
        }

        @Override
        public void updateFill(MetaObject metaObject) {
        }
    }
}
