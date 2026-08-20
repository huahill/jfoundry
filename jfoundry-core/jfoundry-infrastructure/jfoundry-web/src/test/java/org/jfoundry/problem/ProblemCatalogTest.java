package org.jfoundry.problem;

import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemCatalogTest {

    @Test
    void resolvesCoreExceptionsToStableProblemDescriptors() {
        ProblemDescriptor problem = ProblemCatalog.forException(new InvalidArgumentException("pageSize is invalid"));

        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(problem.title()).isEqualTo("Invalid argument");
        assertThat(problem.type()).hasToString("urn:jfoundry:problem:invalid-argument");
        assertThat(problem.detail()).isEqualTo("pageSize is invalid");
    }

    @Test
    void masksExternalAccessDiagnosticsByDefault() {
        ProblemDescriptor problem = ProblemCatalog.forException(
                new ExternalAccessException("k8s api https://cluster.internal timed out"));

        assertThat(problem.detail()).isEqualTo("The requested operation is temporarily unavailable.");
    }

    @Test
    void usesAReviewedExternalAccessPublicDetail() {
        ExternalAccessException exception = new ReviewedExternalAccessException(
                "MKS deployment JWT signing failed", new IllegalStateException("private key is invalid"),
                "Deployment authorization is temporarily unavailable.");

        ProblemDescriptor problem = ProblemCatalog.forException(exception);

        assertThat(problem.detail()).isEqualTo("Deployment authorization is temporarily unavailable.");
    }

    @Test
    void resolvesStandardHttpStatusesToSafeProblemDescriptors() {
        ProblemDescriptor problem = ProblemCatalog.forHttpStatus(405);

        assertThat(problem.status()).isEqualTo(405);
        assertThat(problem.code()).isEqualTo("HTTP_METHOD_NOT_ALLOWED");
        assertThat(problem.detail()).isEqualTo("The HTTP method is not allowed for this resource.");
    }

    @Test
    void resolvesServiceUnavailableToTheServiceUnavailableProblem() {
        ProblemDescriptor problem = ProblemCatalog.forHttpStatus(503);

        assertThat(problem.code()).isEqualTo("HTTP_SERVICE_UNAVAILABLE");
        assertThat(problem.type()).hasToString("urn:jfoundry:problem:http-service-unavailable");
        assertThat(problem.detail()).isEqualTo("The service is temporarily unavailable.");
    }

    @Test
    void identifiesTheHttpStatusesWithSharedProblemSemantics() {
        assertThat(ProblemCatalog.supportsHttpStatus(400)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(404)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(405)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(406)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(413)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(415)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(503)).isTrue();
        assertThat(ProblemCatalog.supportsHttpStatus(403)).isFalse();
    }

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }
}
