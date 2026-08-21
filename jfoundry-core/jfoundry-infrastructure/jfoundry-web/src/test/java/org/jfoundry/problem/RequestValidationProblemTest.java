package org.jfoundry.problem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationProblemTest {

    @Test
    void createsTheSharedValidationProblemContract() {
        ProblemDescriptor problem = RequestValidationProblem.create(List.of(
                RequestValidationProblem.Error.atPath(List.of("services", "0", "image/url"), "must be valid"),
                RequestValidationProblem.Error.forRequest("request fields are inconsistent")));

        assertThat(problem.type()).isEqualTo(RequestValidationProblem.TYPE);
        assertThat(problem.title()).isEqualTo("Request validation failed");
        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.detail()).isEqualTo("The request failed validation. See 'errors' for details.");
        assertThat(problem.extensions().get("errors")).isEqualTo(List.of(
                Map.of("pointer", "#/services/0/image~1url", "detail", "must be valid"),
                Map.of("detail", "request fields are inconsistent")));
    }

    @Test
    void escapesJsonPointerTokens() {
        ProblemDescriptor problem = RequestValidationProblem.create(List.of(
                RequestValidationProblem.Error.atPath(List.of("metadata", "a~b/c"), "is invalid")));

        assertThat(problem.extensions().get("errors")).isEqualTo(List.of(
                Map.of("pointer", "#/metadata/a~0b~1c", "detail", "is invalid")));
    }
}
