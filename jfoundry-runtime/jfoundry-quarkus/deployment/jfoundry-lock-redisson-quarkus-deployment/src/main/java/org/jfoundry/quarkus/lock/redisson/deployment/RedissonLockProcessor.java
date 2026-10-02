package org.jfoundry.quarkus.lock.redisson.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusRedissonLockProducer;

/// Registers the Redisson lock producer with Quarkus during augmentation.
class RedissonLockProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerRedissonLockProducer() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusRedissonLockProducer.class)
                .setUnremovable()
                .build();
    }
}
