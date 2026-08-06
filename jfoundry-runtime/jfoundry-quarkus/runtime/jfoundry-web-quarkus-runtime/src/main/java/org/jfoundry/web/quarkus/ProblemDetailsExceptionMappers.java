package org.jfoundry.web.quarkus;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.ProblemCatalog;

/// Groups the precise Jakarta REST exception mappers used for JFoundry problem responses.
public final class ProblemDetailsExceptionMappers {

    private ProblemDetailsExceptionMappers() {
    }

    private abstract static class JFoundryExceptionMapper<E extends Exception> implements ExceptionMapper<E> {

        @Override
        public final Response toResponse(E exception) {
            return ProblemDetailsResponses.forException(exception);
        }
    }

    /// Maps invalid application arguments to problem responses.
    public static final class InvalidArgumentMapper extends JFoundryExceptionMapper<InvalidArgumentException> {
    }

    /// Maps missing application resources to problem responses.
    public static final class NotFoundMapper extends JFoundryExceptionMapper<NotFoundException> {
    }

    /// Maps application conflicts to problem responses.
    public static final class ConflictMapper extends JFoundryExceptionMapper<ConflictException> {
    }

    /// Maps unavailable external dependencies to problem responses.
    public static final class ExternalAccessMapper extends JFoundryExceptionMapper<ExternalAccessException> {
    }

    /// Maps domain rule violations to problem responses.
    public static final class DomainRuleViolationMapper extends JFoundryExceptionMapper<DomainRuleViolationException> {
    }

    /// Maps invalid domain state transitions to problem responses.
    public static final class DomainStateMapper extends JFoundryExceptionMapper<DomainStateException> {
    }

    /// Maps unhandled exceptions to safe internal-server-error problem responses.
    public static final class UnhandledExceptionMapper extends JFoundryExceptionMapper<Exception> {
    }

    /// Maps supported Jakarta REST failures to problem responses.
    public static final class WebApplicationMapper implements ExceptionMapper<WebApplicationException> {

        @Override
        public Response toResponse(WebApplicationException exception) {
            Response source = exception.getResponse();
            if (!ProblemCatalog.supportsHttpStatus(source.getStatus())) {
                return source;
            }
            return ProblemDetailsResponses.forHttpStatus(source.getStatus(), source.getHeaders());
        }
    }
}
