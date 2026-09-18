package org.jfoundry.application.exception;

/**
 * Indicates that use-case input arguments are invalid.
 */
public class InvalidArgumentException extends ApplicationException {

    public InvalidArgumentException(String message) {
        super(message);
    }

    public InvalidArgumentException(String message, Throwable cause) {
        super(message, cause);
    }

    /// Creates an invalid-argument failure identified by a stable message code with interpolation arguments.
    /// The code is resolved to caller-facing text at the HTTP boundary through message catalogs.
    public InvalidArgumentException(String code, Object... args) {
        super(code, args);
    }
}
