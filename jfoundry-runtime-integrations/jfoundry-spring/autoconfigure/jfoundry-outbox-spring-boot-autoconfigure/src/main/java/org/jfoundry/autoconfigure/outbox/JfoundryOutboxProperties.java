package org.jfoundry.autoconfigure.outbox;

import org.jfoundry.autoconfigure.outbox.persistence.OutboxMybatisPlusAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Outbox configuration.
///
/// Prefix: `jfoundry.outbox`.
///
/// Business applications may override the physical table used by the OutboxData
/// entity through `tableName`. `OutboxMybatisPlusAutoConfiguration` registers a
/// `DynamicTableNameInnerInterceptor` that rewrites the framework logical table
/// name `jfoundry_outbox_event` at runtime.
@ConfigurationProperties(prefix = "jfoundry.outbox")
public class JfoundryOutboxProperties {

    /// Physical table name for the OutboxData entity. Defaults to
    /// `jfoundry_outbox_event`. When customized, business applications must create the table
    /// themselves through Flyway, Liquibase, or manual DDL. SQL templates are packaged under
    /// `jfoundry/sql/outbox/{database}/create_outbox_event.sql`; the schema must match the default
    /// outbox table.
    private String tableName = "jfoundry_outbox_event";

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
}
