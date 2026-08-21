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
                Map.of("detail", "request fields are inconsistent"),
                Map.of("pointer", "#/services/0/image~1url", "detail", "must be valid")));
    }

    @Test
    void escapesJsonPointerTokens() {
        ProblemDescriptor problem = RequestValidationProblem.create(List.of(
                RequestValidationProblem.Error.atPath(List.of("metadata", "a~b/c"), "is invalid")));

        assertThat(problem.extensions().get("errors")).isEqualTo(List.of(
                Map.of("pointer", "#/metadata/a~0b~1c", "detail", "is invalid")));
    }

    @Test
    void percentEncodesJsonPointerUriFragments() {
        ProblemDescriptor problem = RequestValidationProblem.create(List.of(
                RequestValidationProblem.Error.atPath(List.of("metadata", "a b%c", "中文"), "is invalid")));

        assertThat(problem.extensions().get("errors")).isEqualTo(List.of(
                Map.of("pointer", "#/metadata/a%20b%25c/%E4%B8%AD%E6%96%87", "detail", "is invalid")));
    }

    @Test
    void sortsErrorsByDocumentPathAndDetail() {
        ProblemDescriptor problem = RequestValidationProblem.create(List.of(
                RequestValidationProblem.Error.atPath(List.of("services"), "must not be empty"),
                RequestValidationProblem.Error.forRequest("parameters are inconsistent"),
                RequestValidationProblem.Error.atPath(List.of("a/b"), "same path text"),
                RequestValidationProblem.Error.atPath(List.of("a", "b"), "same path text"),
                RequestValidationProblem.Error.atPath(List.of("metadata"), "is invalid"),
                RequestValidationProblem.Error.forRequest("fields are inconsistent")));

        assertThat(problem.extensions().get("errors")).isEqualTo(List.of(
                Map.of("detail", "fields are inconsistent"),
                Map.of("detail", "parameters are inconsistent"),
                Map.of("pointer", "#/a/b", "detail", "same path text"),
                Map.of("pointer", "#/a~1b", "detail", "same path text"),
                Map.of("pointer", "#/metadata", "detail", "is invalid"),
                Map.of("pointer", "#/services", "detail", "must not be empty")));
    }
}
