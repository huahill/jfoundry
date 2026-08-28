package org.jfoundry.integration.dependencies;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringIntegrationDependencyScopeTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.1.0";

    @Test
    void doesNotOverrideJpaSupportRuntimeDependencyWithTestScope() throws Exception {
        assertThat(findDependencyScope(modulePom(), null, "jfoundry-jpa-spring-boot-support")).isNull();
    }

    @Test
    void baseTestDependenciesIncludePersistenceAutoConfiguration() throws Exception {
        assertThat(findDependencyScope(modulePom(), null,
                "jfoundry-persistence-spring-boot-autoconfigure")).isEqualTo("test");
    }

    @Test
    void nativeMybatisProfileKeepsPersistenceSupportOnCompileClasspath() throws Exception {
        assertThat(findDependencyScope(modulePom(), "native-mybatis-plus",
                "jfoundry-persistence-spring")).isEqualTo("compile");
        assertThat(findDependencyScope(modulePom(), "native-mybatis-plus",
                "jfoundry-persistence-spring-boot-autoconfigure")).isEqualTo("compile");
    }

    private Path modulePom() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests/pom.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            candidate = current.resolve(
                    "jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests/pom.xml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Spring integration test POM");
    }

    private Element findProfile(Document document, String profileId) {
        NodeList profiles = document.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "profile");
        for (int index = 0; index < profiles.getLength(); index++) {
            Element profile = (Element) profiles.item(index);
            NodeList ids = profile.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "id");
            if (ids.getLength() > 0 && profileId.equals(ids.item(0).getTextContent())) {
                return profile;
            }
        }
        return null;
    }

    private String findDependencyScope(Element profile, String artifactId) {
        NodeList dependencies = profile.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            NodeList artifactIds = dependency.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "artifactId");
            if (artifactIds.getLength() > 0 && artifactId.equals(artifactIds.item(0).getTextContent())) {
                NodeList scopes = dependency.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "scope");
                return scopes.getLength() == 0 ? "compile" : scopes.item(0).getTextContent();
            }
        }
        return null;
    }

    private String findDependencyScope(Path pom, String profileId, String artifactId) throws Exception {
        if (pom.getFileName().toString().endsWith(".yaml")) {
            Map<String, Object> project = yamlDocument(pom);
            Map<String, Object> container = project;
            if (profileId != null) {
                container = mappings(project, "profiles").stream()
                        .filter(profile -> profileId.equals(value(profile, "id", null)))
                        .findFirst()
                        .orElse(null);
            }
            if (container == null) {
                return null;
            }
            return mappings(container, "dependencies").stream()
                    .filter(dependency -> artifactId.equals(value(dependency, "artifactId", null)))
                    .findFirst()
                    .map(dependency -> value(dependency, "scope", "compile"))
                    .orElse(null);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(pom.toFile());
        Element container = profileId == null ? document.getDocumentElement() : findProfile(document, profileId);
        return container == null ? null : findDependencyScope(container, artifactId);
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
        Object value = parent.get(name);
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private String value(Map<String, Object> parent, String name, String defaultValue) {
        Object value = parent.get(name);
        return value == null ? defaultValue : value.toString();
    }
}
