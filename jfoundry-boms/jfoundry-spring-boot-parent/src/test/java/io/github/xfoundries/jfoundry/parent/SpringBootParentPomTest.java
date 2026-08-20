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
    void inheritsTheSupportedSpringBootParentAndImportsTheBootRuntimeLine() throws Exception {
        Document document = document(Path.of("pom.xml"));

        assertThat(coordinate(child(document.getDocumentElement(), "parent"))).isEqualTo(
                new Coordinate("org.springframework.boot", "spring-boot-starter-parent", "4.1.0"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "jfoundry.version"))
                .isEqualTo("1.2.0-SNAPSHOT");
        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-spring-boot-dependencies", "${jfoundry.version}"),
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${jfoundry.version}"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "java.version")).isEqualTo("25");
    }

    @Test
    void frameworkBuildParentDoesNotImportARuntimeBom() throws Exception {
        Document document = document(Path.of("..", "..", "pom.xml"));

        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${project.version}"));
    }

    @Test
    void frameworkNeutralInfrastructureParentDoesNotImportSpringBootBom() throws Exception {
        Document document = document(Path.of("..", "..", "jfoundry-core", "jfoundry-infrastructure", "pom.xml"));

        assertThat(importedBoms(document)).isEmpty();
    }

    @Test
    void standalonePublishedPomsUseTheReleaseTag() throws Exception {
        List<Path> pomPaths = List.of(
                Path.of("pom.xml"),
                Path.of("..", "jfoundry-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-modules-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-helidon-dependencies", "pom.xml"));

        for (Path pomPath : pomPaths) {
            Document document = document(pomPath);
            assertThat(childText(child(document.getDocumentElement(), "scm"), "tag"))
                    .as("SCM tag for %s", pomPath)
                    .isEqualTo("v1.1.0");
        }
    }

    @Test
    void runtimeBomsDelegateSharedComponentFamiliesToTheCoreBom() throws Exception {
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document boot = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));
        Document cloud = document(Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"));

        assertThat(childText(child(foundation.getDocumentElement(), "properties"), "jobrunr.version"))
                .isEqualTo("8.8.1");
        assertThat(managesDependency(foundation, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isTrue();
        assertThat(managesDependency(foundation, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isTrue();
        assertThat(managesDependency(foundation, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-spring-boot-starter")).isTrue();
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-spring")).isTrue();

        assertThat(importedBoms(boot)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        assertThat(importedBoms(cloud)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        for (Document springLine : List.of(boot, cloud)) {
            assertThat(managesDependency(springLine, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isFalse();
            assertThat(managesDependency(springLine, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isFalse();
            assertThat(managesDependency(springLine, "org.mybatis", "mybatis-spring")).isFalse();
            assertThat(managesDependency(springLine, "org.redisson", "redisson-spring-boot-starter")).isFalse();
            assertThat(managesDependency(springLine, "org.jmolecules.integrations", "jmolecules-spring")).isFalse();
        }
    }

    @Test
    void cloudBomOwnsOnlyCloudPlatformVersions() throws Exception {
        Document document = document(Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"));

        assertThat(child(child(document.getDocumentElement(), "properties"), "spring-boot.version"))
                .isNull();
        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("org.springframework.cloud", "spring-cloud-dependencies", "${spring-cloud.version}"),
                new Coordinate("com.alibaba.cloud", "spring-cloud-alibaba-dependencies", "${spring-cloud-alibaba.version}"));
    }

    @Test
    void springAutoconfigurationUsesTheMybatisPlusStarterInsteadOfManagingMybatisSpring() throws Exception {
        Path autoconfigure = Path.of("..", "..", "jfoundry-runtime", "jfoundry-spring", "autoconfigure");
        Document persistence = document(autoconfigure.resolve(
                "jfoundry-persistence-mybatis-plus-spring-boot-autoconfigure/pom.xml"));
        Document inbox = document(autoconfigure.resolve("jfoundry-inbox-spring-boot-autoconfigure/pom.xml"));
        Document outbox = document(autoconfigure.resolve("jfoundry-outbox-spring-boot-autoconfigure/pom.xml"));

        assertThat(dependency(persistence, "org.mybatis", "mybatis-spring")).isNull();
        assertThat(dependency(persistence, "com.baomidou", "mybatis-plus-spring-boot4-starter"))
                .satisfies(it -> assertThat(childText(it, "scope")).isEqualTo("test"));

        for (Document document : List.of(inbox, outbox)) {
            assertThat(dependency(document, "org.mybatis", "mybatis-spring")).isNull();
            assertThat(dependency(document, "com.baomidou", "mybatis-plus-spring-boot4-starter"))
                    .satisfies(it -> {
                        assertThat(childText(it, "scope")).isEqualTo("provided");
                        assertThat(childText(it, "optional")).isEqualTo("true");
                    });
        }
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

    private Element dependency(Document document, String groupId, String artifactId) {
        Element dependencies = child(document.getDocumentElement(), "dependencies");
        return children(dependencies, "dependency").stream()
                .filter(candidate -> groupId.equals(childText(candidate, "groupId")))
                .filter(candidate -> artifactId.equals(childText(candidate, "artifactId")))
                .findFirst()
                .orElse(null);
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
