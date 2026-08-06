package org.jfoundry.autoconfigure.persistence.mybatis;

import org.jfoundry.infrastructure.persistence.AggregateData;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusNativeRuntimeHintsTest {

    @Test
    void registersAggregateDataIdForMybatisPlusNativeImageMapping() throws Exception {
        RuntimeHints hints = new RuntimeHints();

        new MybatisPlusNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                .onFieldAccess(AggregateData.class, "id")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AggregateData.class, "getId")
                .test(hints)).isTrue();
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(AggregateData.class, "setId")
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
