package org.jfoundry.web.helidon;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
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

    @Provider
    public static final class InvalidArgumentMapper extends JFoundryExceptionMapper<InvalidArgumentException> { }
    @Provider
    public static final class NotFoundMapper extends JFoundryExceptionMapper<NotFoundException> { }
    @Provider
    public static final class ConflictMapper extends JFoundryExceptionMapper<ConflictException> { }
    @Provider
    public static final class ExternalAccessMapper extends JFoundryExceptionMapper<ExternalAccessException> { }
    @Provider
    public static final class DomainRuleViolationMapper extends JFoundryExceptionMapper<DomainRuleViolationException> { }
    @Provider
    public static final class DomainStateMapper extends JFoundryExceptionMapper<DomainStateException> { }
    @Provider
    public static final class UnhandledExceptionMapper extends JFoundryExceptionMapper<Exception> { }

    @Provider
    public static final class WebApplicationMapper implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException exception) {
            Response source = exception.getResponse();
            return ProblemCatalog.supportsHttpStatus(source.getStatus())
                    ? ProblemDetailsResponses.forHttpStatus(source.getStatus(), source.getHeaders())
                    : source;
        }
    }
}
