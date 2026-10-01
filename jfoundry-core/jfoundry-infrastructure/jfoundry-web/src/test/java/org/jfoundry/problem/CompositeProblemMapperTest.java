package org.jfoundry.problem;

import org.jfoundry.application.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeProblemMapperTest {

    @Test
    void givesApplicationMappersPrecedenceOverBuiltInMappings() {
        ProblemDescriptor applicationProblem = new ProblemDescriptor(
                URI.create("https://example.test/problems/invalid-request"), "Invalid request", 422,
                "The supplied request cannot be processed.", Map.of("retryable", false));
        ProblemMapper applicationMapper = exception -> Optional.of(applicationProblem);
        CompositeProblemMapper mapper = new CompositeProblemMapper(java.util.List.of(applicationMapper));

        assertThat(mapper.map(new InvalidArgumentException("internal detail"))).contains(applicationProblem);
    }

    @Test
    void fallsBackToASafeInternalServerErrorWithoutEchoingExceptionText() {
        CompositeProblemMapper mapper = new CompositeProblemMapper(java.util.List.of());

        ProblemDescriptor result = mapper.map(new IllegalStateException("database password=secret")).orElseThrow();

        assertThat(result.status()).isEqualTo(500);
        assertThat(result.type()).hasToString("urn:jfoundry:problem:internal-error");
        assertThat(result.extensions()).isEmpty();
        assertThat(result.detail()).doesNotContain("database password=secret");
    }

    @Test
    void localizesTheDefaultMappingAndInternalErrorFallbackForTheSuppliedLocale() {
        CompositeProblemMapper mapper = new CompositeProblemMapper(java.util.List.of(),
                ProblemMessageResolver.framework(), () -> java.util.Locale.SIMPLIFIED_CHINESE);

        ProblemDescriptor mapped = mapper.map(new InvalidArgumentException("pageSize is invalid")).orElseThrow();
        assertThat(mapped.title()).isEqualTo("参数无效");

        ProblemDescriptor internalError = mapper.map(new IllegalStateException("secret")).orElseThrow();
        assertThat(internalError.type()).hasToString("urn:jfoundry:problem:internal-error");
        assertThat(internalError.title()).isEqualTo("服务器内部错误");
        assertThat(internalError.detail()).isEqualTo("服务器处理请求时出错。");
    }

    @Test
    void keepsExtensionsImmutableAndProtectsReservedRfcMembers() {
        ProblemDescriptor descriptor = new ProblemDescriptor(
                URI.create("urn:jfoundry:problem:test"), "Test", 400, "Test detail", Map.of("retryable", false));

        assertThatThrownBy(() -> descriptor.extensions().put("retryable", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ProblemDescriptor(
                URI.create("urn:jfoundry:problem:test"), "Test", 400, "Test detail", Map.of("status", 400)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
