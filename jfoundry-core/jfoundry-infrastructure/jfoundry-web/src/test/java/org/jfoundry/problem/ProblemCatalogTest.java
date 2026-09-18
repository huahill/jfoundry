package org.jfoundry.problem;

import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemCatalogTest {

    @Test
    void resolvesCoreExceptionsToStableProblemDescriptors() {
        ProblemDescriptor problem = ProblemCatalog.forException(new InvalidArgumentException("pageSize is invalid"));

        assertThat(problem.status()).isEqualTo(400);
        assertThat(problem.title()).isEqualTo("Invalid argument");
        assertThat(problem.type()).hasToString("urn:jfoundry:problem:invalid-argument");
        assertThat(problem.detail()).isEqualTo("pageSize is invalid");
        assertThat(problem.extensions()).isEmpty();
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
        assertThat(problem.type()).hasToString("urn:jfoundry:problem:http-method-not-allowed");
        assertThat(problem.detail()).isEqualTo("The HTTP method is not allowed for this resource.");
        assertThat(problem.extensions()).isEmpty();
    }

    @Test
    void resolvesServiceUnavailableToTheServiceUnavailableProblem() {
        ProblemDescriptor problem = ProblemCatalog.forHttpStatus(503);

        assertThat(problem.type()).hasToString("urn:jfoundry:problem:http-service-unavailable");
        assertThat(problem.detail()).isEqualTo("The service is temporarily unavailable.");
        assertThat(problem.extensions()).isEmpty();
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

    @Test
    void resolvesFrameworkStringsThroughTheFrameworkCatalogForTheRequestedLocale() {
        ProblemDescriptor problem = ProblemCatalog.forException(new InvalidArgumentException("pageSize is invalid"),
                java.util.Locale.SIMPLIFIED_CHINESE, ProblemMessageResolver.framework());

        assertThat(problem.title()).isEqualTo("参数无效");
        assertThat(problem.detail()).isEqualTo("pageSize is invalid");
        assertThat(problem.extensions()).isEmpty();
    }

    @Test
    void resolvesHttpStatusStringsForTheRequestedLocale() {
        ProblemDescriptor problem = ProblemCatalog.forHttpStatus(404, java.util.Locale.SIMPLIFIED_CHINESE,
                ProblemMessageResolver.framework());

        assertThat(problem.title()).isEqualTo("资源不存在");
        assertThat(problem.detail()).isEqualTo("请求的资源不存在。");
    }

    @Test
    void resolvesCodedDetailsAndExposesCodeAndArgsAsExtensionMembers() {
        DomainRuleViolationException exception =
                new DomainRuleViolationException("order.quota-exceeded", 2, 2);
        ProblemMessageResolver messages = codedResolver("配额已超出:" );

        ProblemDescriptor problem = ProblemCatalog.forException(exception, java.util.Locale.SIMPLIFIED_CHINESE,
                messages);

        assertThat(problem.title()).isEqualTo("违反领域规则");
        assertThat(problem.detail()).isEqualTo("配额已超出:[2, 2]");
        assertThat(problem.extensions())
                .containsEntry("code", "order.quota-exceeded")
                .containsEntry("args", java.util.List.of(2, 2));
    }

    @Test
    void fallsBackToTheLanguageIndependentMessageWhenACodeCannotBeResolved() {
        DomainRuleViolationException exception =
                new DomainRuleViolationException("order.quota-exceeded", 2, 2);

        ProblemDescriptor problem = ProblemCatalog.forException(exception, java.util.Locale.SIMPLIFIED_CHINESE,
                ProblemMessageResolver.unresolved());

        assertThat(problem.detail()).isEqualTo("order.quota-exceeded [2, 2]");
        assertThat(problem.extensions())
                .containsEntry("code", "order.quota-exceeded")
                .containsEntry("args", java.util.List.of(2, 2));
    }

    @Test
    void resolvesExternalAccessPublicDetailCodes() {
        CodedExternalAccessException exception = new CodedExternalAccessException(
                "payment platform unavailable", new RuntimeException("token endpoint returned 500"),
                "payment.gateway-timeout", "sandbox");
        ProblemMessageResolver messages = ProblemMessageResolver.composite(
                (code, locale, args) -> args.isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of("支付网关暂时不可用:" + args.get(0)),
                ProblemMessageResolver.framework());

        ProblemDescriptor problem = ProblemCatalog.forException(exception, java.util.Locale.SIMPLIFIED_CHINESE,
                messages);

        assertThat(problem.title()).isEqualTo("服务暂时不可用");
        assertThat(problem.detail()).isEqualTo("支付网关暂时不可用:sandbox");
        assertThat(problem.extensions())
                .containsEntry("code", "payment.gateway-timeout")
                .containsEntry("args", java.util.List.of("sandbox"));
    }

    @Test
    void keepsMaskedExternalAccessFailuresFreeOfCodesAndArgs() {
        ProblemDescriptor problem = ProblemCatalog.forException(
                new ExternalAccessException("k8s api https://cluster.internal timed out"),
                java.util.Locale.SIMPLIFIED_CHINESE, ProblemMessageResolver.framework());

        assertThat(problem.title()).isEqualTo("服务暂时不可用");
        assertThat(problem.detail()).isEqualTo("请求的操作暂时不可用。");
        assertThat(problem.extensions()).isEmpty();
    }

    private static ProblemMessageResolver codedResolver(String prefix) {
        return ProblemMessageResolver.composite(
                (code, locale, args) -> args.isEmpty()
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(prefix + args),
                ProblemMessageResolver.framework());
    }

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }

    private static final class CodedExternalAccessException extends ExternalAccessException {

        private CodedExternalAccessException(String message, Throwable cause, String publicDetailCode,
                Object... publicDetailArgs) {
            super(message, cause, publicDetailCode, publicDetailArgs);
        }
    }
}
