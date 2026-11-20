package io.huahill.jfoundry.infrastructure.outbox.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MybatisPlusOutboxMessageStoreFailFastTest {

    @Test
    void constructorFailsWhenPaginationInnerInterceptorMissing() {
        OutboxMapper mapper = mock(OutboxMapper.class);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); // no inner interceptors

        assertThatThrownBy(() -> new MybatisPlusOutboxMessageStore(mapper, interceptor))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PaginationInnerInterceptor");
    }
}
