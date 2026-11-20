package io.huahill.jfoundry.application.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxSqlTemplateClasspathTest {

    @Test
    void exposesTheDocumentedOutboxTemplates() {
        assertThat(getClass().getClassLoader().getResource("jfoundry/sql/outbox/mysql/create_outbox_event.sql"))
                .isNotNull();
        assertThat(getClass().getClassLoader().getResource("jfoundry/sql/outbox/postgresql/create_outbox_event.sql"))
                .isNotNull();
    }
}
