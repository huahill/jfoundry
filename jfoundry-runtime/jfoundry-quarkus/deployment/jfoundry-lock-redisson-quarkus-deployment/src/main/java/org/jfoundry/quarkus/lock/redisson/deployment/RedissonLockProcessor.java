package org.jfoundry.quarkus.lock.redisson.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import org.jboss.jandex.AnnotationTransformation;
import org.jfoundry.application.lock.DistributedLock;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusDistributedLock;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusDistributedLockInterceptor;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusRedissonLockProducer;

/// Registers the Redisson lock producer and annotation interceptor during Quarkus augmentation.
class RedissonLockProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerRedissonLockBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass(QuarkusRedissonLockProducer.class)
                .addBeanClass(QuarkusDistributedLockInterceptor.class)
                .setUnremovable()
                .build();
    }

    @BuildStep
    AnnotationsTransformerBuildItem bindDistributedLockMethods() {
        return new AnnotationsTransformerBuildItem(
                AnnotationTransformation.forMethods()
                        .whenAnyMatch(DistributedLock.class)
                        .transform(transformation -> transformation.add(QuarkusDistributedLock.class)));
    }

    @BuildStep
    NativeImageResourceBuildItem registerExpressionFactoryService() {
        return new NativeImageResourceBuildItem("META-INF/services/jakarta.el.ExpressionFactory");
    }
}
