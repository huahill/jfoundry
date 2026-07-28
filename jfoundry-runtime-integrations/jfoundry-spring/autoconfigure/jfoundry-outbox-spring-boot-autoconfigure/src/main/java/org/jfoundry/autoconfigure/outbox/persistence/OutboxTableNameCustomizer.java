package org.jfoundry.autoconfigure.outbox.persistence;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.jfoundry.autoconfigure.outbox.JfoundryOutboxProperties;

import java.util.ArrayList;
import java.util.List;

/// Adds the outbox dynamic table-name rewrite to a MyBatis-Plus interceptor.
public final class OutboxTableNameCustomizer {

    /// Logical table name declared by OutboxData.
    public static final String OUTBOX_LOGICAL_TABLE = "jfoundry_outbox_event";

    private final JfoundryOutboxProperties properties;

    public OutboxTableNameCustomizer(JfoundryOutboxProperties properties) {
        this.properties = properties;
    }

    public void customize(MybatisPlusInterceptor interceptor) {
        if (interceptor == null) {
            return;
        }
        if (interceptor.getInterceptors().stream().anyMatch(OutboxDynamicTableNameInnerInterceptor.class::isInstance)) {
            return;
        }
        List<InnerInterceptor> interceptors = new ArrayList<>(interceptor.getInterceptors());
        interceptors.add(0, dynamicTableNameInnerInterceptor());
        interceptor.setInterceptors(interceptors);
    }

    private OutboxDynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor() {
        TableNameHandler handler = (sqlStatement, tableName) ->
                OUTBOX_LOGICAL_TABLE.equals(tableName) ? properties.getTableName() : tableName;
        return new OutboxDynamicTableNameInnerInterceptor(handler);
    }

    private static final class OutboxDynamicTableNameInnerInterceptor extends DynamicTableNameInnerInterceptor {
        private OutboxDynamicTableNameInnerInterceptor(TableNameHandler tableNameHandler) {
            super(tableNameHandler);
        }
    }
}
