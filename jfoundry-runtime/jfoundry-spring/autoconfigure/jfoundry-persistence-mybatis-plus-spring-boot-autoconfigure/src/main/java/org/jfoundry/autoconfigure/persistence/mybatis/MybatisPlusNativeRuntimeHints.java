package org.jfoundry.autoconfigure.persistence.mybatis;

import org.jfoundry.infrastructure.persistence.AggregateData;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class MybatisPlusNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(AggregateData.class,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerTypeIfPresent(classLoader, "org.mybatis.spring.SqlSessionTemplate",
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
