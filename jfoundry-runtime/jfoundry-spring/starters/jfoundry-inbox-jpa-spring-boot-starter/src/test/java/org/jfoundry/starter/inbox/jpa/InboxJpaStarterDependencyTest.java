package org.jfoundry.starter.inbox.jpa;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InboxJpaStarterDependencyTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void declaresTheExplicitJPAInboxCapabilityDependencies() throws Exception {
        Map<String, DependencyDeclaration> dependencies = dependencyDeclarations(Path.of("pom.xml"));

        assertCompileNonOptional(dependencies,
                "jfoundry-inbox-spring-boot-starter",
                "jfoundry-inbox-jpa",
                "spring-boot-starter-data-jpa");
    }

    @Test
    void businessJpaStarterDoesNotIncludeReliableMessagingStores() throws Exception {
        assertThat(dependencyDeclarations(Path.of("..", "jfoundry-persistence-jpa-spring-boot-starter", "pom.xml")))
                .doesNotContainKeys("jfoundry-outbox-jpa", "jfoundry-inbox-jpa");
    }

    private void assertCompileNonOptional(Map<String, DependencyDeclaration> dependencies, String... artifactIds) {
        for (String artifactId : artifactIds) {
            assertThat(dependencies).containsEntry(artifactId,
                    new DependencyDeclaration(artifactId, "compile", false));
        }
    }

    private Map<String, DependencyDeclaration> dependencyDeclarations(Path pom) throws Exception {
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
