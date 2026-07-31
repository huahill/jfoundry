package org.jfoundry.autoconfigure.outbox.persistence;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMybatisPlusAutoConfigurationTest {

    @Test
    void bindsMapperScanToTheDefaultSqlSessionFactoryForAot() {
        MapperScan mapperScan = OutboxMybatisPlusAutoConfiguration.class
                .getAnnotation(MapperScan.class);

        assertThat(mapperScan.sqlSessionFactoryRef()).isEqualTo("sqlSessionFactory");
    }
}
