package org.jfoundry.integration.nativeimage.mybatisplus;

import org.jfoundry.infrastructure.persistence.AuditActorProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/// Native Image consumer used to certify the JFoundry MyBatis-Plus Spring Boot starter.
@SpringBootApplication
@MapperScan(basePackageClasses = NativeAuditRecordMapper.class, sqlSessionFactoryRef = "sqlSessionFactory")
public class NativeMybatisPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeMybatisPlusApplication.class, args);
    }

    @Bean
    AuditActorProvider nativeAuditActorProvider() {
        return () -> Optional.of("native-test");
    }
}
