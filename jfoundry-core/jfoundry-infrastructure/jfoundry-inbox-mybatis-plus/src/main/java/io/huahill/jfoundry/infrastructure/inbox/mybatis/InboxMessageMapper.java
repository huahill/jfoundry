package io.huahill.jfoundry.infrastructure.inbox.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboxMessageMapper extends BaseMapper<InboxMessageData> {
}
