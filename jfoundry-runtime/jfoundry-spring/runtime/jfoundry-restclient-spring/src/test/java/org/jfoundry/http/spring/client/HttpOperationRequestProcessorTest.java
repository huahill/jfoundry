package org.jfoundry.http.spring.client;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.web.service.invoker.HttpRequestValues;

import static org.assertj.core.api.Assertions.assertThat;

class HttpOperationRequestProcessorTest {

    @Test
    void recordsTheInterfaceMethodAndKeepsAnExistingOperation() throws Exception {
        Method method = SampleClient.class.getMethod("createCiEnv");
        HttpRequestValues.Builder builder = HttpRequestValues.builder();
        HttpOperationRequestProcessor.process(method, builder);

        assertThat(builder.build().getAttributes())
                .containsEntry(HttpLoggingInterceptor.OPERATION_ATTRIBUTE, "SampleClient#createCiEnv");

        builder.addAttribute(HttpLoggingInterceptor.OPERATION_ATTRIBUTE, "Existing#call");
        HttpOperationRequestProcessor.process(method, builder);

        assertThat(builder.build().getAttributes())
                .containsEntry(HttpLoggingInterceptor.OPERATION_ATTRIBUTE, "Existing#call");
    }

    interface SampleClient {

        void createCiEnv();
    }
}
