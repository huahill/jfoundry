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
