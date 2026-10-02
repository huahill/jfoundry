package org.jfoundry.quarkus.lock.redisson.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusRedissonLockProducer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonLockProcessorTest {

    @Test
    void registersTheRedissonLockProducer() {
        AdditionalBeanBuildItem beans = new RedissonLockProcessor().registerRedissonLockProducer();

        assertThat(beans.getBeanClasses()).contains(QuarkusRedissonLockProducer.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }
}
