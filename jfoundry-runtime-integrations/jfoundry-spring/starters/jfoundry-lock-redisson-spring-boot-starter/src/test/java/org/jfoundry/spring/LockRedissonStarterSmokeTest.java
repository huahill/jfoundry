package org.jfoundry.spring;

import org.jfoundry.application.lock.LockExecutor;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LockRedissonStarterSmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestApp.class);

    @Test
    void starterLoadsAutoConfigurationWithUserProvidedRedissonClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LockExecutor.class);
        });
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        RedissonClient redissonClient() {
            return mock(RedissonClient.class);
        }
    }
}
