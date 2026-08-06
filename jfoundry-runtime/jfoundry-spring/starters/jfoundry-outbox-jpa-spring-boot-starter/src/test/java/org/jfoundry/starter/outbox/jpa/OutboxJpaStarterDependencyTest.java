package org.jfoundry.starter.outbox.jpa;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxJpaStarterDependencyTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void declaresTheExplicitJPAOutboxCapabilityDependencies() throws Exception {
        Map<String, DependencyDeclaration> dependencies = dependencyDeclarations(Path.of("pom.xml"));

        assertCompileNonOptional(dependencies,
                "jfoundry-outbox-spring-boot-starter",
                "jfoundry-outbox-jpa",
                "spring-boot-starter-data-jpa");
    }

    @Test
    void businessJpaStarterDoesNotIncludeReliableMessagingStores() throws Exception {
        assertThat(dependencyDeclarations(Path.of("..", "jfoundry-persistence-jpa-spring-boot-starter", "pom.xml")))
                .doesNotContainKeys("jfoundry-outbox-jpa", "jfoundry-inbox-jpa");
    }

    @Test
    void businessJpaStarterRuntimeDependencyTreeDoesNotContainReliableMessagingStores() throws Exception {
        Path projectRoot = repositoryRoot();
        Process process = new ProcessBuilder(
                projectRoot.resolve("mvnw").toString(),
                "-pl", "jfoundry-runtime/jfoundry-spring/starters/jfoundry-persistence-jpa-spring-boot-starter",
                "-am",
                "dependency:tree",
                "-Dscope=runtime",
                "-Dincludes=io.github.xfoundries:jfoundry-outbox-jpa,io.github.xfoundries:jfoundry-inbox-jpa")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
        String jpaStarterTree = dependencyTreeFor(output, "jfoundry-persistence-jpa-spring-boot-starter");

        assertThat(jpaStarterTree).contains("jfoundry-persistence-jpa-spring-boot-starter");
        assertThat(jpaStarterTree).doesNotContain("jfoundry-outbox-jpa", "jfoundry-inbox-jpa");
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("mvnw")) && Files.isDirectory(current.resolve(".mvn"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Maven project root");
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

    private String dependencyTreeFor(String output, String artifactId) {
        String section = "--- dependency:3.7.0:tree (default-cli) @ " + artifactId + " ---";
        int start = output.indexOf(section);
        int end = output.indexOf("[INFO] ------------------------------------------------------------------------", start);
        return output.substring(start, end < 0 ? output.length() : end);
    }

    private record DependencyDeclaration(String artifactId, String scope, boolean optional) {
    }
}
