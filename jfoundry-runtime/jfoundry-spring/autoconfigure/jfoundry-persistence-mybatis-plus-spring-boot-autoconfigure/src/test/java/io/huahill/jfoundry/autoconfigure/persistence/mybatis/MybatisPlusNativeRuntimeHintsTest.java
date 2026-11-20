package io.huahill.jfoundry.autoconfigure.persistence.mybatis;

import io.huahill.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAggregateData;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusNativeRuntimeHintsTest {

    @Test
    void registersMybatisPlusAggregateDataIdForMybatisPlusNativeImageMapping() throws Exception {
        RuntimeHints hints = new RuntimeHints();

        new MybatisPlusNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                .onFieldAccess(MybatisPlusAggregateData.class, "id")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(MybatisPlusAggregateData.class, "getId")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(MybatisPlusAggregateData.class, "setId")
                .test(hints)).isTrue();
    }

    @Test
    void registersSqlSessionFactoryAccessorUsedByMybatisPlus() throws Exception {
        RuntimeHints hints = new RuntimeHints();

        new MybatisPlusNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().getTypeHint(
                TypeReference.of("org.mybatis.spring.SqlSessionTemplate")))
                .isNotNull();
        assertThat(hints.reflection().getTypeHint(
                TypeReference.of("org.mybatis.spring.SqlSessionTemplate"))
                .getMemberCategories())
                .contains(MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
