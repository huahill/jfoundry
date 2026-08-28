package org.jfoundry.starter.inbox.jpa;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InboxJpaStarterDependencyTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.1.0";

    @Test
    void declaresTheExplicitJPAInboxCapabilityDependencies() throws Exception {
        Map<String, DependencyDeclaration> dependencies = dependencyDeclarations(descriptor(Path.of(".")));

        assertCompileNonOptional(dependencies,
                "jfoundry-inbox-spring-boot-starter",
                "jfoundry-inbox-jpa",
                "spring-boot-starter-data-jpa");
    }

    @Test
    void businessJpaStarterDoesNotIncludeReliableMessagingStores() throws Exception {
        assertThat(dependencyDeclarations(descriptor(Path.of("..", "jfoundry-persistence-jpa-spring-boot-starter"))))
                .doesNotContainKeys("jfoundry-outbox-jpa", "jfoundry-inbox-jpa");
    }

    private void assertCompileNonOptional(Map<String, DependencyDeclaration> dependencies, String... artifactIds) {
        for (String artifactId : artifactIds) {
            assertThat(dependencies).containsEntry(artifactId,
                    new DependencyDeclaration(artifactId, "compile", false));
        }
    }

    private Map<String, DependencyDeclaration> dependencyDeclarations(Path pom) throws Exception {
        if (pom.getFileName().toString().endsWith(".yaml")) {
            Map<String, DependencyDeclaration> declarations = new LinkedHashMap<>();
            for (Map<String, Object> dependency : mappings(yamlDocument(pom), "dependencies")) {
                String artifactId = value(dependency, "artifactId", null);
                declarations.put(artifactId, new DependencyDeclaration(
                        artifactId,
                        value(dependency, "scope", "compile"),
                        Boolean.parseBoolean(value(dependency, "optional", "false"))));
            }
            return declarations;
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        NodeList dependencies = document.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "dependency");
        Map<String, DependencyDeclaration> declarations = new LinkedHashMap<>();
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            String artifactId = childText(dependency, "artifactId");
            declarations.put(artifactId, new DependencyDeclaration(
                    artifactId,
                    childText(dependency, "scope", "compile"),
                    Boolean.parseBoolean(childText(dependency, "optional", "false"))));
        }
        return declarations;
    }

    private Path descriptor(Path directory) {
        Path yaml = directory.resolve("pom.yaml");
        return Files.exists(yaml) ? yaml : directory.resolve("pom.xml");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yamlDocument(Path path) throws Exception {
        try (Reader reader = Files.newBufferedReader(path)) {
            Class<?> yamlType = Class.forName("org.yaml.snakeyaml.Yaml");
            Object yaml = yamlType.getConstructor().newInstance();
            return (Map<String, Object>) yamlType.getMethod("load", Reader.class).invoke(yaml, reader);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mappings(Map<String, Object> parent, String name) {
        return (List<Map<String, Object>>) parent.get(name);
    }

    private String value(Map<String, Object> parent, String name, String defaultValue) {
        Object value = parent.get(name);
        return value == null ? defaultValue : value.toString();
    }

    private String childText(Element element, String name) {
        return childText(element, name, null);
    }

    private String childText(Element element, String name, String defaultValue) {
        NodeList children = element.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, name);
        return children.getLength() == 0 ? defaultValue : children.item(0).getTextContent();
    }

    private record DependencyDeclaration(String artifactId, String scope, boolean optional) {
    }
}
