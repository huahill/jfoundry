package org.jfoundry.autoconfigure.outbox.persistence;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusOutboxNativeRuntimeHintsTest {

    @Test
    void registersPublicMethodsUsedByDeleteByIdsOgnlExpression() {
        RuntimeHints hints = new RuntimeHints();

        new MybatisPlusOutboxNativeRuntimeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(hints.reflection().getTypeHint(SystemMetaObject.class).getMemberCategories())
                .contains(MemberCategory.INVOKE_PUBLIC_METHODS);
        assertThat(hints.reflection().getTypeHint(MetaObject.class).getMemberCategories())
                .contains(MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
