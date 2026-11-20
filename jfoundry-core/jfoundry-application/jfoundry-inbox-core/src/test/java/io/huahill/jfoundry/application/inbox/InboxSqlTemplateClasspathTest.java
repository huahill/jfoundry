package io.huahill.jfoundry.application.inbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboxSqlTemplateClasspathTest {

    @Test
    void exposesTheDocumentedInboxTemplate() {
        assertThat(getClass().getClassLoader().getResource("jfoundry/sql/inbox/common/create_inbox_message.sql"))
                .isNotNull();
    }
}
