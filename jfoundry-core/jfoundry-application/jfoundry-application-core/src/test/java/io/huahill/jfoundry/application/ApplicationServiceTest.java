package io.huahill.jfoundry.application;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationServiceTest {

    @Test
    void marksTypeAsRuntimeApplicationServiceBoundary() {
        assertThat(ApplicationService.class.getPackageName()).isEqualTo("io.huahill.jfoundry.application");
        assertThat(ApplicationService.class)
                .hasAnnotation(java.lang.annotation.Documented.class);
        assertThat(ApplicationService.class.getAnnotation(java.lang.annotation.Retention.class).value())
                .isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(ApplicationService.class.getAnnotation(java.lang.annotation.Target.class).value())
                .containsExactly(ElementType.TYPE);
    }
}
