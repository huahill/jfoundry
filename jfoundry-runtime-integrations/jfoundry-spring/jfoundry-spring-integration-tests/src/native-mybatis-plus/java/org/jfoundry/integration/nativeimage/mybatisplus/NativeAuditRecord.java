package org.jfoundry.integration.nativeimage.mybatisplus;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.jfoundry.infrastructure.persistence.mybatis.MybatisPlusAuditData;

/// Minimal persistence type that exercises MyBatis-Plus field filling in a Native Image consumer.
@TableName("native_audit_record")
public class NativeAuditRecord extends MybatisPlusAuditData<String> {

    @TableId(type = IdType.INPUT)
    private String id;

    private String content;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
