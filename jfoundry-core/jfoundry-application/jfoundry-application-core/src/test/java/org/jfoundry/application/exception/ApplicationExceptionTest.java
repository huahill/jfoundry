package org.jfoundry.application.exception;

import org.junit.jupiter.api.Test;

import java.io.ObjectStreamClass;
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

    private static final class ReviewedExternalAccessException extends ExternalAccessException {

        private ReviewedExternalAccessException(String message, Throwable cause, String publicDetail) {
            super(message, cause, publicDetail);
        }
    }
}
