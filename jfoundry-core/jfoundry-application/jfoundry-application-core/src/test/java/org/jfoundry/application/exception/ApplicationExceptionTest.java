package org.jfoundry.application.exception;

import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationExceptionTest {

    @Test
    void invalidArgumentPreservesMessageAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("pageSize");

        InvalidArgumentException exception = new InvalidArgumentException("Invalid page size", cause);

        assertInstanceOf(ApplicationException.class, exception);
        assertEquals("Invalid page size", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void notFoundPreservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("missing row");

        NotFoundException exception = new NotFoundException("Environment not found", cause);

        assertInstanceOf(ApplicationException.class, exception);
        assertEquals("Environment not found", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void conflictPreservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("version mismatch");

        ConflictException exception = new ConflictException("Environment was modified", cause);

        assertInstanceOf(ApplicationException.class, exception);
        assertEquals("Environment was modified", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void externalAccessPreservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("remote timeout");

        ExternalAccessException exception = new ExternalAccessException("Container platform failed", cause);

        assertInstanceOf(ApplicationException.class, exception);
        assertEquals("Container platform failed", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(Optional.empty(), exception.publicDetail());
    }

    @Test
    void externalAccessCanExposeAReviewedPublicDetail() {
        RuntimeException cause = new RuntimeException("private key is invalid");

        ExternalAccessException exception = new ReviewedExternalAccessException(
                "MKS deployment JWT signing failed", cause,
                "Deployment authorization is temporarily unavailable.");

        assertEquals("MKS deployment JWT signing failed", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(Optional.of("Deployment authorization is temporarily unavailable."), exception.publicDetail());
    }

    @Test
    void externalAccessPreservesItsSerializationContract() {
        long serialVersionUid = ObjectStreamClass.lookup(ExternalAccessException.class).getSerialVersionUID();

        assertEquals(2547502648268602446L, serialVersionUid);
    }

    @Test
    void externalAccessRejectsAMissingPublicDetail() {
        assertThrows(NullPointerException.class,
                () -> new ReviewedExternalAccessException("External access failed", new RuntimeException(), null));
    }

    @Test
    void externalAccessRejectsABlankPublicDetail() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewedExternalAccessException("External access failed", new RuntimeException(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> new ReviewedExternalAccessException("External access failed", new RuntimeException(), " \t"));
    }

    @Test
    void applicationFailuresCarryCodeAndArguments() {
        InvalidArgumentException exception = new InvalidArgumentException("order.invalid-page-size", 500, 100);

        assertEquals("order.invalid-page-size [500, 100]", exception.getMessage());
        assertEquals(Optional.of("order.invalid-page-size"), exception.problemCode());
        assertEquals(List.of(500, 100), exception.problemArguments());

        NotFoundException notFound = new NotFoundException("user.not-found", "user-42");
        assertEquals(Optional.of("user.not-found"), notFound.problemCode());
        assertEquals(List.of("user-42"), notFound.problemArguments());

        ConflictException conflict = new ConflictException("invoice.already-settled", "INV-1");
        assertEquals(Optional.of("invoice.already-settled"), conflict.problemCode());
        assertEquals(List.of("INV-1"), conflict.problemArguments());
    }

    @Test
    void externalAccessCanExposeAPublicDetailCode() {
        RuntimeException cause = new RuntimeException("token endpoint returned 500");

        CodedExternalAccessException exception = new CodedExternalAccessException(
                "payment platform unavailable", cause, "payment.gateway-timeout", "sandbox");

        assertEquals("payment platform unavailable", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(Optional.empty(), exception.publicDetail());
        assertEquals(Optional.of("payment.gateway-timeout"), exception.publicDetailCode());
        assertEquals(List.of("sandbox"), exception.publicDetailArguments());
    }

    @Test
    void externalAccessRejectsInvalidPublicDetailCodes() {
        assertThrows(IllegalArgumentException.class, () -> new CodedExternalAccessException(
                "External access failed", new RuntimeException(), " ", new Object[0]));
        assertThrows(IllegalArgumentException.class, () -> new CodedExternalAccessException(
                "External access failed", new RuntimeException(), "payment.gateway-timeout", new Object()));
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
