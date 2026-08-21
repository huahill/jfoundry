package org.jfoundry.quarkus.web.deployment;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.resteasy.reactive.spi.ExceptionMapperBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jfoundry.application.exception.InvalidArgumentException;
import org.jfoundry.problem.ProblemDescriptor;
import org.jfoundry.web.quarkus.ProblemDetailsExceptionMappers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailsProcessorTest {

    @Test
    void registersProblemDetailsExceptionMappersForRest() {
        List<ExceptionMapperBuildItem> mappers = new ProblemDetailsProcessor().registerProblemDetailsExceptionMappers();

        assertThat(mappers).anySatisfy(mapper -> {
            assertThat(mapper.getClassName()).isEqualTo(ProblemDetailsExceptionMappers.InvalidArgumentMapper.class.getName());
            assertThat(mapper.getHandledExceptionName()).isEqualTo(InvalidArgumentException.class.getName());
            assertThat(mapper.isRegisterAsBean()).isTrue();
        });
    }

    @Test
    void registersTheProblemDescriptorForJacksonInNativeImage() {
        ReflectiveClassBuildItem reflection = new ProblemDetailsProcessor().registerProblemDescriptorForJackson();

        assertThat(reflection.getClassNames()).contains(ProblemDescriptor.class.getName());
        assertThat(reflection.isMethods()).isTrue();
        assertThat(reflection.isFields()).isTrue();
    }

    @Test
    void registersRequestValidationWhenHibernateValidatorIsPresent() {
        List<ExceptionMapperBuildItem> mappers = new ProblemDetailsProcessor()
                .registerRequestValidationMapper(new Capabilities(Set.of(Capability.HIBERNATE_VALIDATOR)));

        assertThat(mappers).singleElement().satisfies(mapper -> {
            assertThat(mapper.getClassName()).isEqualTo(ProblemDetailsProcessor.REQUEST_VALIDATION_MAPPER);
            assertThat(mapper.getHandledExceptionName())
                    .isEqualTo(ProblemDetailsProcessor.REST_REQUEST_VALIDATION_EXCEPTION);
            assertThat(mapper.isRegisterAsBean()).isTrue();
        });
    }

    @Test
    void skipsRequestValidationWhenHibernateValidatorIsMissing() {
        List<ExceptionMapperBuildItem> mappers = new ProblemDetailsProcessor()
                .registerRequestValidationMapper(new Capabilities(Set.of()));

        assertThat(mappers).isEmpty();
    }
}
