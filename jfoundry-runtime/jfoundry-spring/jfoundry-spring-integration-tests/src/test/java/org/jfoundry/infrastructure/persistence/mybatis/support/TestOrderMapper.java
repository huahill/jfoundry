package org.jfoundry.infrastructure.persistence.mybatis.support;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TestOrderMapper extends BaseMapper<TestOrderData> {
}
