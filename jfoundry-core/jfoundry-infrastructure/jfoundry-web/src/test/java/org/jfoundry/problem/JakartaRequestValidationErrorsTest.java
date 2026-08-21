package org.jfoundry.problem;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JakartaRequestValidationErrorsTest {

    @Test
    void rendersDocumentPathsOnlyForProvenRequestDocumentViolations() {
        ConstraintViolation<?> bodyViolation = violation("must be valid",
                node(ElementKind.METHOD, "create"),
                node(ElementKind.PARAMETER, "request"),
                node(ElementKind.PROPERTY, "services"),
                indexedNode(ElementKind.CONTAINER_ELEMENT, "<list element>", 0),
                node(ElementKind.PROPERTY, "image/url"));
        ConstraintViolation<?> queryViolation = violation("must not be empty",
                node(ElementKind.METHOD, "find"),
                node(ElementKind.PARAMETER, "filter"),
                node(ElementKind.PROPERTY, "name"));

        List<RequestValidationProblem.Error> errors = JakartaRequestValidationErrors.from(
                List.of(queryViolation, bodyViolation), violation -> violation == bodyViolation);

        assertThat(errors).containsExactly(
                RequestValidationProblem.Error.forRequest("must not be empty"),
                RequestValidationProblem.Error.atPath(List.of("services", "0", "image/url"), "must be valid"));
        assertThat(RequestValidationProblem.create(errors).extensions().get("errors")).isEqualTo(List.of(
                Map.of("detail", "must not be empty"),
                Map.of("pointer", "#/services/0/image~1url", "detail", "must be valid")));
    }

    @Test
    void rendersMapKeysAndNestedPropertiesAfterTheExecutableParameter() {
        ConstraintViolation<?> violation = violation("is invalid",
                node(ElementKind.METHOD, "create"),
                node(ElementKind.PARAMETER, "request"),
                node(ElementKind.PROPERTY, "metadata"),
                keyedNode(ElementKind.CONTAINER_ELEMENT, "<map value>", "a~b/c"),
                node(ElementKind.PROPERTY, "value"));

        assertThat(JakartaRequestValidationErrors.from(List.of(violation), ignored -> true))
                .containsExactly(RequestValidationProblem.Error.atPath(
                        List.of("metadata", "a~b/c", "value"), "is invalid"));
    }

    @Test
    void rendersClassAndCrossParameterConstraintsWithoutPointers() {
        ConstraintViolation<?> classViolation = violation("fields are inconsistent",
                node(ElementKind.METHOD, "create"),
                node(ElementKind.PARAMETER, "request"),
                node(ElementKind.BEAN, null));
        ConstraintViolation<?> crossParameterViolation = violation("parameters are inconsistent",
                node(ElementKind.METHOD, "create"),
                node(ElementKind.CROSS_PARAMETER, "<cross-parameter>"));

        assertThat(JakartaRequestValidationErrors.from(
                List.of(crossParameterViolation, classViolation), ignored -> true))
                .containsExactly(
                        RequestValidationProblem.Error.forRequest("fields are inconsistent"),
                        RequestValidationProblem.Error.forRequest("parameters are inconsistent"));
    }

    @Test
    void sortsErrorsAndFallsBackWhenTheProviderReturnsNoMessage() {
        ConstraintViolation<?> later = violation("z message",
                node(ElementKind.PARAMETER, "request"), node(ElementKind.PROPERTY, "z"));
        ConstraintViolation<?> earlier = violation(null,
                node(ElementKind.PARAMETER, "request"), node(ElementKind.PROPERTY, "a"));

        assertThat(JakartaRequestValidationErrors.from(List.of(later, earlier), ignored -> true))
                .containsExactly(
                        RequestValidationProblem.Error.atPath(List.of("a"), "is invalid"),
                        RequestValidationProblem.Error.atPath(List.of("z"), "z message"));
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<?> violation(String message, Path.Node... nodes) {
        Path path = () -> List.of(nodes).iterator();
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(
                JakartaRequestValidationErrorsTest.class.getClassLoader(),
                new Class<?>[]{ConstraintViolation.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMessage" -> message;
                    case "getPropertyPath" -> path;
                    case "toString" -> "ConstraintViolation[" + message + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                });
    }

    private static Path.Node node(ElementKind kind, String name) {
        return new TestNode(kind, name, false, null, null);
    }

    private static Path.Node indexedNode(ElementKind kind, String name, int index) {
        return new TestNode(kind, name, true, index, null);
    }

    private static Path.Node keyedNode(ElementKind kind, String name, Object key) {
        return new TestNode(kind, name, true, null, key);
    }

    private record TestNode(ElementKind kind, String name, boolean inIterable, Integer index, Object key)
            implements Path.Node {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isInIterable() {
            return inIterable;
        }

        @Override
        public Integer getIndex() {
            return index;
        }

        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public ElementKind getKind() {
            return kind;
        }

        @Override
        public <T extends Path.Node> T as(Class<T> nodeType) {
            return nodeType.cast(this);
        }
    }
}
