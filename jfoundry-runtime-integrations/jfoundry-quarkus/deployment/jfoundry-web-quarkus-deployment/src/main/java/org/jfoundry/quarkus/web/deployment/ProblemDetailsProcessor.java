package org.jfoundry.quarkus.web.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import org.jfoundry.application.exception.ConflictException;
import org.jfoundry.application.exception.ExternalAccessException;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.application.exception.NotFoundException;
import org.jfoundry.domain.exception.DomainRuleViolationException;
import org.jfoundry.domain.exception.DomainStateException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.web.quarkus.ProblemDetailsExceptionMappers;

import jakarta.ws.rs.WebApplicationException;

import java.util.List;

/// Registers Problem Details exception mappers with Quarkus during augmentation.
class ProblemDetailsProcessor {

    @BuildStep
    List<ExceptionMapperBuildItem> registerProblemDetailsExceptionMappers() {
        return List.of(
                mapper(ProblemDetailsExceptionMappers.InvalidArgumentMapper.class, InvalidArgumentException.class),
                mapper(ProblemDetailsExceptionMappers.NotFoundMapper.class, NotFoundException.class),
                mapper(ProblemDetailsExceptionMappers.ConflictMapper.class, ConflictException.class),
                mapper(ProblemDetailsExceptionMappers.ExternalAccessMapper.class, ExternalAccessException.class),
                mapper(ProblemDetailsExceptionMappers.DomainRuleViolationMapper.class, DomainRuleViolationException.class),
                mapper(ProblemDetailsExceptionMappers.DomainStateMapper.class, DomainStateException.class),
                mapper(ProblemDetailsExceptionMappers.UnhandledExceptionMapper.class, Exception.class),
                mapper(ProblemDetailsExceptionMappers.WebApplicationMapper.class, WebApplicationException.class)
        );
    }

    @BuildStep
    ReflectiveClassBuildItem registerProblemDescriptorForJackson() {
        return ReflectiveClassBuildItem.builder(ProblemDescriptor.class)
                .methods()
                .fields()
                .build();
    }

    private static ExceptionMapperBuildItem mapper(Class<?> mapperType, Class<? extends Throwable> exceptionType) {
        return new ExceptionMapperBuildItem(mapperType.getName(), exceptionType.getName(), null, true);
    }
}
