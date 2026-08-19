package io.github.xfoundries.jfoundry.parent;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootParentPomTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void inheritsTheSupportedSpringBootParentAndImportsBothJFoundryBoms() throws Exception {
        Document document = document(Path.of("pom.xml"));

        assertThat(coordinate(child(document.getDocumentElement(), "parent"))).isEqualTo(
                new Coordinate("org.springframework.boot", "spring-boot-starter-parent", "4.0.7"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "jfoundry.version"))
                .isEqualTo("1.0.3");
        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-spring-dependencies", "${jfoundry.version}"),
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${jfoundry.version}"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "java.version")).isEqualTo("25");
    }

    @Test
    void standalonePublishedPomsUseTheReleaseTag() throws Exception {
        List<Path> pomPaths = List.of(
                Path.of("pom.xml"),
                Path.of("..", "jfoundry-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-modules-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-spring-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-helidon-dependencies", "pom.xml"));

        for (Path pomPath : pomPaths) {
            Document document = document(pomPath);
            assertThat(childText(child(document.getDocumentElement(), "scm"), "tag"))
                    .as("SCM tag for %s", pomPath)
                    .isEqualTo("v1.0.3");
        }
    }

    @Test
    void foundationBomOwnsSharedComponentFamiliesUsedBySpring() throws Exception {
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document spring = document(Path.of("..", "jfoundry-spring-dependencies", "pom.xml"));

        assertThat(childText(child(foundation.getDocumentElement(), "properties"), "jobrunr.version"))
                .isEqualTo("8.8.1");
        assertThat(managesDependency(foundation, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isTrue();
        assertThat(managesDependency(foundation, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isTrue();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-spring-boot-starter")).isTrue();
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-spring")).isTrue();

        assertThat(importedBoms(spring)).contains(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        assertThat(managesDependency(spring, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isFalse();
        assertThat(managesDependency(spring, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isFalse();
        assertThat(managesDependency(spring, "org.redisson", "redisson-spring-boot-starter")).isFalse();
        assertThat(managesDependency(spring, "org.jmolecules.integrations", "jmolecules-spring")).isFalse();
    }

    private Document document(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private List<Coordinate> importedBoms(Document document) {
        Element project = document.getDocumentElement();
        Element management = child(project, "dependencyManagement");
        if (management == null) {
            return List.of();
        }
        Element dependencies = child(management, "dependencies");
        List<Coordinate> imports = new ArrayList<>();
        for (Element dependency : children(dependencies, "dependency")) {
            Element scope = child(dependency, "scope");
            if (scope != null && "import".equals(scope.getTextContent())) {
                imports.add(coordinate(dependency));
            }
        }
        return imports;
    }

    private Coordinate coordinate(Element element) {
        return new Coordinate(
                childText(element, "groupId"),
                childText(element, "artifactId"),
                childText(element, "version"));
    }

    private boolean managesDependency(Document document, String groupId, String artifactId) {
        Element management = child(document.getDocumentElement(), "dependencyManagement");
        Element dependencies = child(management, "dependencies");
        return children(dependencies, "dependency").stream()
                .anyMatch(dependency -> groupId.equals(childText(dependency, "groupId"))
                        && artifactId.equals(childText(dependency, "artifactId")));
    }

    private Element child(Element parent, String name) {
        return children(parent, name).stream().findFirst().orElse(null);
    }

    private List<Element> children(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        for (Element element : elements(parent)) {
            if (name.equals(element.getLocalName())) {
                children.add(element);
            }
        }
        return children;
    }

    private List<Element> elements(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (int index = 0; index < parent.getChildNodes().getLength(); index++) {
            if (parent.getChildNodes().item(index) instanceof Element element
                    && MAVEN_POM_NAMESPACE.equals(element.getNamespaceURI())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private String childText(Element parent, String name) {
        return child(parent, name).getTextContent();
    }

    private record Coordinate(String groupId, String artifactId, String version) {
    }
}
