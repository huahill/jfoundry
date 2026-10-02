package org.jfoundry.quarkus.lock.redisson.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import org.jboss.jandex.AnnotationOverlay;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jfoundry.application.lock.DistributedLock;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusDistributedLock;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusDistributedLockInterceptor;
import org.jfoundry.infrastructure.lock.redisson.quarkus.QuarkusRedissonLockProducer;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonLockProcessorTest {

    @Test
    void registersTheRedissonLockBeans() {
        AdditionalBeanBuildItem beans = new RedissonLockProcessor().registerRedissonLockBeans();

        assertThat(beans.getBeanClasses()).contains(
                QuarkusRedissonLockProducer.class.getName(),
                QuarkusDistributedLockInterceptor.class.getName());
        assertThat(beans.isRemovable()).isFalse();
    }

    @Test
    void addsTheInterceptorBindingOnlyToDistributedLockMethods() throws Exception {
        Indexer indexer = new Indexer();
        String resource = LockedOperation.class.getName().replace('.', '/') + ".class";
        try (InputStream input = LockedOperation.class.getClassLoader().getResourceAsStream(resource)) {
            indexer.index(input);
        }
        Index index = indexer.complete();
        AnnotationOverlay overlay = AnnotationOverlay.builder(index, java.util.List.of(
                new RedissonLockProcessor().bindDistributedLockMethods().getAnnotationTransformation())).build();
        ClassInfo type = index.getClassByName(DotName.createSimple(LockedOperation.class.getName()));
        MethodInfo locked = type.methods().stream().filter(method -> method.name().equals("locked")).findFirst().orElseThrow();
        MethodInfo plain = type.methods().stream().filter(method -> method.name().equals("plain")).findFirst().orElseThrow();

        assertThat(overlay.hasAnnotation(locked, QuarkusDistributedLock.class)).isTrue();
        assertThat(overlay.hasAnnotation(plain, QuarkusDistributedLock.class)).isFalse();
    }

    @Test
    void registersTheExpressionFactoryServiceResource() {
        assertThat(new RedissonLockProcessor().registerExpressionFactoryService().getResources())
                .contains("META-INF/services/jakarta.el.ExpressionFactory");
    }

    static class LockedOperation {

        @DistributedLock(key = "order")
        void locked(String orderId) {
        }

        void plain() {
        }
    }
}
