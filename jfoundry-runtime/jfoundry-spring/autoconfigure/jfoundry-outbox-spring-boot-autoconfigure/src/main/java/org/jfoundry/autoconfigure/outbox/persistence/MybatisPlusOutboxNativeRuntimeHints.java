package org.jfoundry.autoconfigure.outbox.persistence;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

final class MybatisPlusOutboxNativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // DeleteByIds reaches these types only through its dynamically generated OGNL expression.
        hints.reflection().registerType(SystemMetaObject.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.reflection().registerType(MetaObject.class,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
