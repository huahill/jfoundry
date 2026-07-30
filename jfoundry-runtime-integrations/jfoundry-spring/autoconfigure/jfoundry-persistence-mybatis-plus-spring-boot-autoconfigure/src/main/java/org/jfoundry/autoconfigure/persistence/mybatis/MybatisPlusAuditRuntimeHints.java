package org.jfoundry.autoconfigure.persistence.mybatis;

import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAuditData;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/// Registers the inherited JFoundry audit mapping members required by MyBatis-Plus in a Native Image.
public final class MybatisPlusAuditRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerType(MybatisPlusAuditData.class);
    }
}
