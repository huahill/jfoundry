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

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.1.0";

    @Test
    void inheritsTheSupportedSpringBootParentAndImportsTheBootRuntimeLine() throws Exception {
        Document parent = document(Path.of("pom.xml"));
        Document bom = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));
        Coordinate springBootParent = coordinate(child(parent.getDocumentElement(), "parent"));
        String bomVersion = childText(child(bom.getDocumentElement(), "properties"), "spring-boot.version");

        assertThat(springBootParent).isEqualTo(
                new Coordinate("org.springframework.boot", "spring-boot-starter-parent", bomVersion));
        assertThat(childText(child(parent.getDocumentElement(), "properties"), "jfoundry.version"))
                .isEqualTo(childText(parent.getDocumentElement(), "version"));
        assertThat(importedBoms(parent)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-spring-boot-dependencies", "${jfoundry.version}"),
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${jfoundry.version}"));
        assertThat(childText(child(parent.getDocumentElement(), "properties"), "java.version")).isEqualTo("25");
    }

    @Test
    void frameworkBuildParentDoesNotImportARuntimeBom() throws Exception {
        Document document = document(Path.of("..", "..", "pom.xml"));

        assertThat(importedBoms(document)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${project.version}"),
                new Coordinate("org.junit", "junit-bom", "${junit-jupiter.version}"),
                new Coordinate("tools.jackson", "jackson-bom", "${jackson3.version}"),
                new Coordinate("io.opentelemetry", "opentelemetry-bom", "${opentelemetry.version}"));
    }

    @Test
    void frameworkBuildParentDoesNotPinJacksonOrJunitCoordinates() throws Exception {
        Document document = document(Path.of("..", "..", "pom.xml"));

        assertThat(managesDependency(document, "org.junit.jupiter", "junit-jupiter")).isFalse();
        assertThat(managesDependency(document, "tools.jackson.core", "jackson-databind")).isFalse();
        assertThat(managesDependency(document, "io.opentelemetry", "opentelemetry-api")).isFalse();
        assertThat(managesDependency(document, "org.apache.rocketmq", "rocketmq-client")).isTrue();
    }

    @Test
    void foundationDoesNotManageSelectedRuntimePlatformStacks() throws Exception {
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));

        assertThat(importedBoms(foundation)).containsExactly(
                new Coordinate("org.jmolecules", "jmolecules-bom", "${jmolecules.version}"));
        assertThat(managesDependency(foundation, "tools.jackson.core", "jackson-databind")).isFalse();
        assertThat(managesDependency(foundation, "org.junit.jupiter", "junit-jupiter")).isFalse();
        assertThat(managesDependency(foundation, "org.mockito", "mockito-core")).isFalse();
        assertThat(managesDependency(foundation, "org.slf4j", "slf4j-api")).isFalse();
        assertThat(managesDependency(foundation, "io.opentelemetry", "opentelemetry-api")).isFalse();
        assertThat(managesDependency(foundation, "org.apache.kafka", "kafka-clients")).isFalse();
        assertThat(managesDependency(foundation, "com.rabbitmq", "amqp-client")).isFalse();
        assertThat(managesDependency(foundation, "org.apache.rocketmq", "rocketmq-client")).isFalse();
        assertThat(managesDependency(foundation, "com.h2database", "h2")).isFalse();
        assertThat(managesDependency(foundation, "org.hibernate.orm", "hibernate-core")).isFalse();
    }

    @Test
    void frameworkNeutralInfrastructureParentDoesNotImportSpringBootBom() throws Exception {
        Document document = document(Path.of("..", "..", "jfoundry-core", "jfoundry-infrastructure", "pom.xml"));

        assertThat(importedBoms(document)).isEmpty();
    }

    @Test
    void independentPublishedPomsUseVersionAppropriateScmTags() throws Exception {
        List<Path> pomPaths = List.of(
                Path.of("pom.xml"),
                Path.of("..", "..", "pom.xml"),
                Path.of("..", "jfoundry-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-modules-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"),
                Path.of("..", "jfoundry-helidon-dependencies", "pom.xml"));
        String reactorVersion = childText(
                document(Path.of("..", "..", "pom.xml")).getDocumentElement(), "version");
        String expectedLiteralTag = expectedLiteralScmTag(reactorVersion);

        for (Path pomPath : pomPaths) {
            Document document = document(pomPath);
            assertThat(childText(document.getDocumentElement(), "version"))
                    .as("project version for %s", pomPath)
                    .isEqualTo(reactorVersion);
            assertThat(childText(child(document.getDocumentElement(), "scm"), "tag"))
                    .as("SCM tag for %s", pomPath)
                    .isIn("v${project.version}", expectedLiteralTag);
        }
    }

    private String expectedLiteralScmTag(String reactorVersion) {
        if (!reactorVersion.endsWith("-SNAPSHOT")) {
            return "v" + reactorVersion;
        }

        String[] parts = reactorVersion
                .substring(0, reactorVersion.length() - "-SNAPSHOT".length())
                .split("\\.");
        assertThat(parts).as("SNAPSHOT version segments").hasSize(3);
        assertThat(parts[2]).as("SNAPSHOT patch version").isEqualTo("0");
        int minor = Integer.parseInt(parts[1]);
        assertThat(minor).as("SNAPSHOT minor version").isPositive();
        return "v%s.%d.0".formatted(parts[0], minor - 1);
    }

    @Test
    void springRuntimeBomOwnsSpringSpecificComponentFamilies() throws Exception {
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document boot = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));
        Document cloud = document(Path.of("..", "jfoundry-spring-cloud-dependencies", "pom.xml"));

        assertThat(managesDependency(foundation, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isFalse();
        assertThat(managesDependency(foundation, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isFalse();
        assertThat(managesDependency(foundation, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-spring-boot-starter")).isFalse();
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-spring")).isFalse();
        assertThat(importedBoms(foundation)).contains(
                new Coordinate("org.jmolecules", "jmolecules-bom", "${jmolecules.version}"));
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-archunit")).isFalse();
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-jackson3")).isFalse();
        assertThat(property(foundation, "jmolecules-integrations.version")).isNull();

        assertThat(managesDependency(boot, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isTrue();
        assertThat(managesDependency(boot, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isTrue();
        assertThat(managesDependency(boot, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(managesDependency(boot, "org.redisson", "redisson-spring-boot-starter")).isTrue();
        assertThat(managesDependency(boot, "org.jmolecules.integrations", "jmolecules-spring")).isTrue();
        assertThat(managesDependency(boot, "org.apache.kafka", "kafka-clients")).isFalse();
        assertThat(managesDependency(boot, "com.rabbitmq", "amqp-client")).isFalse();
        assertThat(managesDependency(boot, "org.apache.rocketmq", "rocketmq-client")).isFalse();
        assertThat(managesDependency(foundation, "org.apache.rocketmq", "rocketmq-client")).isFalse();

        assertThat(importedBoms(boot)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        assertThat(importedBoms(cloud)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        for (Document springLine : List.of(boot, cloud)) {
            assertThat(managesDependency(springLine, "org.jobrunr", "jobrunr-spring-boot-4-starter"))
                    .isEqualTo(springLine == boot);
            assertThat(managesDependency(springLine, "com.baomidou", "mybatis-plus-spring-boot4-starter"))
                    .isEqualTo(springLine == boot);
            assertThat(managesDependency(springLine, "org.mybatis", "mybatis-spring")).isFalse();
            assertThat(managesDependency(springLine, "org.redisson", "redisson-spring-boot-starter"))
                    .isEqualTo(springLine == boot);
            assertThat(managesDependency(springLine, "org.jmolecules.integrations", "jmolecules-spring"))
                    .isEqualTo(springLine == boot);
        }
    }

    @Test
    void quarkusRuntimeBomOwnsRedissonExtensionsAfterThePlatformImport() throws Exception {
        Document quarkus = document(Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"));
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document boot = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));

        assertThat(managesDependency(quarkus, "org.redisson", "redisson-quarkus-33")).isTrue();
        assertThat(managesDependency(quarkus, "org.redisson", "redisson-quarkus-33-deployment")).isTrue();
        assertThat(managesDependency(quarkus, "org.redisson", "redisson")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-quarkus-33")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-quarkus-33-deployment")).isFalse();
        assertThat(managesDependency(boot, "org.redisson", "redisson-quarkus-33")).isFalse();
        assertThat(managesDependency(boot, "org.redisson", "redisson-quarkus-33-deployment")).isFalse();
        assertThat(childText(child(quarkus.getDocumentElement(), "properties"), "redisson.version"))
                .isEqualTo(childText(child(foundation.getDocumentElement(), "properties"), "redisson.version"));

        List<String> managed = managedArtifactIds(quarkus);
        assertThat(managed).containsSubsequence(
                "jackson-annotations",
                "quarkus-bom",
                "redisson-quarkus-33",
                "redisson-quarkus-33-deployment");
    }

    @Test
    void quarkusRuntimeBomOwnsJobRunrExtensionsAfterThePlatformImport() throws Exception {
        Document quarkus = document(Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"));
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document boot = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));

        assertThat(managesDependency(quarkus, "org.jobrunr", "quarkus-jobrunr")).isTrue();
        assertThat(managesDependency(quarkus, "org.jobrunr", "quarkus-jobrunr-deployment")).isTrue();
        assertThat(managesDependency(foundation, "org.jobrunr", "quarkus-jobrunr")).isFalse();
        assertThat(managesDependency(foundation, "org.jobrunr", "quarkus-jobrunr-deployment")).isFalse();
        assertThat(managesDependency(boot, "org.jobrunr", "quarkus-jobrunr")).isFalse();
        assertThat(managesDependency(boot, "org.jobrunr", "quarkus-jobrunr-deployment")).isFalse();
        assertThat(childText(child(quarkus.getDocumentElement(), "properties"), "jobrunr.version"))
                .isEqualTo(childText(child(foundation.getDocumentElement(), "properties"), "jobrunr.version"));
        assertThat(managedArtifactIds(quarkus)).containsSubsequence(
                "redisson-quarkus-33-deployment",
                "quarkus-jobrunr",
                "quarkus-jobrunr-deployment");
    }

    @Test
    void helidonRuntimeBomOwnsTheRedissonIntegrationAfterThePlatformImport() throws Exception {
        Document helidon = document(Path.of("..", "jfoundry-helidon-dependencies", "pom.xml"));
        Document foundation = document(Path.of("..", "jfoundry-foundation-dependencies", "pom.xml"));
        Document boot = document(Path.of("..", "jfoundry-spring-boot-dependencies", "pom.xml"));
        Document quarkus = document(Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"));

        assertThat(managesDependency(helidon, "org.redisson", "redisson-helidon-40")).isTrue();
        assertThat(managesDependency(helidon, "org.redisson", "redisson")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-helidon-40")).isFalse();
        assertThat(managesDependency(boot, "org.redisson", "redisson-helidon-40")).isFalse();
        assertThat(managesDependency(quarkus, "org.redisson", "redisson-helidon-40")).isFalse();
        assertThat(childText(child(helidon.getDocumentElement(), "properties"), "redisson.version"))
                .isEqualTo(childText(child(foundation.getDocumentElement(), "properties"), "redisson.version"));

        assertThat(managedArtifactIds(helidon)).containsSubsequence(
                "helidon-dependencies",
                "redisson-helidon-40");
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
    void quarkusRuntimeBuildUsesTheBuildParentAndConsumerBomPlatformVersion() throws Exception {
        Document runtime = document(Path.of("..", "..", "jfoundry-runtime", "jfoundry-quarkus", "pom.xml"));
        Document bom = document(Path.of("..", "jfoundry-quarkus-dependencies", "pom.xml"));

        assertThat(property(runtime, "quarkus.version")).isNull();
        assertThat(childText(child(runtime.getDocumentElement(), "parent"), "relativePath"))
                .isEqualTo("../../jfoundry-boms/jfoundry-quarkus-build/pom.xml");
        assertThat(importedBoms(runtime)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-quarkus-dependencies", "${project.version}"));

        Document build = document(Path.of("..", "jfoundry-quarkus-build", "pom.xml"));
        String buildVersion = childText(child(build.getDocumentElement(), "properties"), "quarkus.version");
        String bomVersion = childText(child(bom.getDocumentElement(), "properties"), "quarkus.version");
        assertThat(buildVersion).as("Quarkus build parent and consumer BOM versions").isEqualTo(bomVersion);
        assertThat(childText(child(build.getDocumentElement(), "parent"), "relativePath"))
                .isEqualTo("../../pom.xml");
        assertThat(managedPlugins(build)).contains(
                new Coordinate("io.quarkus", "quarkus-maven-plugin", "${quarkus.version}"),
                new Coordinate("io.quarkus", "quarkus-extension-maven-plugin", "${quarkus.version}"));
        assertThat(managesDependency(build, "io.quarkus", "quarkus-extension-processor")).isTrue();
    }

    @Test
    void helidonRuntimeBuildUsesTheConsumerBomAsItsPlatformVersionSource() throws Exception {
        Document runtime = document(Path.of("..", "..", "jfoundry-runtime", "jfoundry-helidon", "pom.xml"));
        Document bom = document(Path.of("..", "jfoundry-helidon-dependencies", "pom.xml"));

        assertThat(property(runtime, "helidon.version")).isNull();
        assertThat(importedBoms(runtime)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-helidon-dependencies", "${project.version}"));
        assertThat(importedBoms(bom)).containsExactly(
                new Coordinate("io.helidon", "helidon-dependencies", "${helidon.version}"));
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

    private List<Coordinate> managedPlugins(Document document) {
        Element build = child(document.getDocumentElement(), "build");
        Element pluginManagement = build == null ? null : child(build, "pluginManagement");
        if (pluginManagement == null) {
            return List.of();
        }
        Element plugins = child(pluginManagement, "plugins");
        List<Coordinate> managed = new ArrayList<>();
        for (Element plugin : children(plugins, "plugin")) {
            Element groupId = child(plugin, "groupId");
            managed.add(new Coordinate(
                    groupId == null ? "org.apache.maven.plugins" : groupId.getTextContent(),
                    childText(plugin, "artifactId"),
                    childText(plugin, "version")));
        }
        return managed;
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

    private List<String> managedArtifactIds(Document document) {
        Element management = child(document.getDocumentElement(), "dependencyManagement");
        Element dependencies = child(management, "dependencies");
        List<String> artifactIds = new ArrayList<>();
        for (Element dependency : children(dependencies, "dependency")) {
            artifactIds.add(childText(dependency, "artifactId"));
        }
        return artifactIds;
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

    private Element property(Document document, String name) {
        Element properties = child(document.getDocumentElement(), "properties");
        return properties == null ? null : child(properties, name);
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
