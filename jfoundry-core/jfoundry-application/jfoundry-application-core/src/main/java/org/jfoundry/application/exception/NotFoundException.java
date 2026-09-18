package org.jfoundry.application.exception;

/**
 * Indicates that data required by a use case cannot be found.
 */
public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Creates a not-found failure identified by a stable message code with interpolation arguments.
    /// The code is resolved to caller-facing text at the HTTP boundary through message catalogs.
    public NotFoundException(String code, Object... args) {
        super(code, args);
    }
}
