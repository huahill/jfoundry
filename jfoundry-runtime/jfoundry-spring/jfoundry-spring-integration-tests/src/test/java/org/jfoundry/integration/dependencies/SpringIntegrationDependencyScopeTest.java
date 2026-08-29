package org.jfoundry.integration.dependencies;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpringIntegrationDependencyScopeTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.1.0";

    @Test
    void doesNotOverrideJpaSupportRuntimeDependencyWithTestScope() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(modulePom().toFile());
        NodeList dependencies = document.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "dependency");

        assertThat(hasDirectDependency(dependencies, "jfoundry-jpa-spring-boot-support")).isFalse();
    }

    @Test
    void baseTestDependenciesIncludePersistenceAutoConfiguration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(modulePom().toFile());

        assertThat(findDependencyScope(document.getDocumentElement(),
                "jfoundry-persistence-spring-boot-autoconfigure")).isEqualTo("test");
    }

    @Test
    void nativeMybatisProfileKeepsPersistenceSupportOnCompileClasspath() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(modulePom().toFile());

        Element profile = findProfile(document, "native-mybatis-plus");
        assertThat(profile).isNotNull();
        assertThat(findDependencyScope(profile, "jfoundry-persistence-spring")).isEqualTo("compile");
        assertThat(findDependencyScope(profile,
                "jfoundry-persistence-spring-boot-autoconfigure")).isEqualTo("compile");
    }

    private Path modulePom() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "jfoundry-runtime/jfoundry-spring/jfoundry-spring-integration-tests/pom.xml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Spring integration test POM");
    }

    private boolean hasDirectDependency(NodeList dependencies, String artifactId) {
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            NodeList artifactIds = dependency.getElementsByTagNameNS(MAVEN_POM_NAMESPACE, "artifactId");
            if (artifactIds.getLength() > 0 && artifactId.equals(artifactIds.item(0).getTextContent())) {
                return true;
            }
        }
        return false;
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
}
