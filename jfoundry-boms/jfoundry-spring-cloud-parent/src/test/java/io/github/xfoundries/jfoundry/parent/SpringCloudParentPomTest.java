package io.github.xfoundries.jfoundry.parent;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringCloudParentPomTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void inheritsTheSupportedSpringBootParentAndImportsTheCloudRuntimeLine() throws Exception {
        Document document = document(Path.of("pom.xml"));

        assertThat(coordinate(child(document.getDocumentElement(), "parent"))).isEqualTo(
                new Coordinate("org.springframework.boot", "spring-boot-starter-parent", "4.0.7"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "jfoundry.version"))
                .isEqualTo("1.1.0");
        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-spring-cloud-dependencies", "${jfoundry.version}"),
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${jfoundry.version}"));
        assertThat(childText(child(document.getDocumentElement(), "properties"), "java.version")).isEqualTo("25");
    }

    @Test
    void isAStandalonePublishedPom() throws Exception {
        Document document = document(Path.of("pom.xml"));

        assertThat(childText(child(document.getDocumentElement(), "scm"), "tag")).isEqualTo("v1.1.0");
        assertThat(child(document.getDocumentElement(), "licenses")).isNotNull();
        assertThat(child(document.getDocumentElement(), "developers")).isNotNull();
    }

    @Test
    void cloudBomUsesTheSameSpringBootVersionAsTheParent() throws Exception {
        Document parent = document(Path.of("pom.xml"));
        Document cloudBom = document(Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"));

        String parentVersion = childText(child(parent.getDocumentElement(), "parent"), "version");
        String bomVersion = childText(child(cloudBom.getDocumentElement(), "properties"), "spring-boot.version");

        assertThat(bomVersion).isEqualTo(parentVersion);
    }

    private Document document(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private List<Coordinate> importedBoms(Document document) {
        Element dependencies = child(child(document.getDocumentElement(), "dependencyManagement"), "dependencies");
        List<Coordinate> imports = new ArrayList<>();
        for (Element dependency : children(dependencies, "dependency")) {
            if ("import".equals(childText(dependency, "scope"))) {
                imports.add(coordinate(dependency));
            }
        }
        return imports;
    }

    private Coordinate coordinate(Element element) {
        return new Coordinate(childText(element, "groupId"), childText(element, "artifactId"), childText(element, "version"));
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
