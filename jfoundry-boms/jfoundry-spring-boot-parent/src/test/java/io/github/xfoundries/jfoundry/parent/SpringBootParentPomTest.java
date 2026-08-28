package io.github.xfoundries.jfoundry.parent;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootParentPomTest {

    private static final String MAVEN_POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void inheritsTheSupportedSpringBootParentAndImportsTheBootRuntimeLine() throws Exception {
        Path parent = descriptor(Path.of("."));
        Path bom = descriptor(Path.of("..", "jfoundry-spring-boot-dependencies"));
        Coordinate springBootParent = coordinate(parent, "parent");
        String bomVersion = property(bom, "spring-boot.version");

        assertThat(springBootParent).isEqualTo(
                new Coordinate("org.springframework.boot", "spring-boot-starter-parent", bomVersion));
        assertThat(bomVersion).isEqualTo("4.1.1");
        assertThat(property(parent, "jfoundry.version")).isEqualTo(projectVersion(parent));
        assertThat(importedBoms(parent)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-spring-boot-dependencies", "${jfoundry.version}"),
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${jfoundry.version}"));
        assertThat(property(parent, "java.version")).isEqualTo("25");
    }

    @Test
    void publishedParentDoesNotExposeItsYamlTestParser() throws Exception {
        Path parent = descriptor(Path.of("."));

        assertThat(hasDependency(parent, "org.yaml", "snakeyaml")).isFalse();
    }

    @Test
    void frameworkBuildParentDoesNotImportARuntimeBom() throws Exception {
        assertThat(importedBoms(descriptor(Path.of("..", "..")))).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-dependencies", "${project.version}"));
    }

    @Test
    void frameworkNeutralInfrastructureParentDoesNotImportSpringBootBom() throws Exception {
        Path descriptor = descriptor(Path.of("..", "..", "jfoundry-core", "jfoundry-infrastructure"));

        assertThat(importedBoms(descriptor)).isEmpty();
    }

    @Test
    void independentPublishedPomsUseVersionAppropriateScmTags() throws Exception {
        List<Path> pomPaths = List.of(
                descriptor(Path.of(".")),
                descriptor(Path.of("..", "..")),
                descriptor(Path.of("..", "jfoundry-dependencies")),
                descriptor(Path.of("..", "jfoundry-foundation-dependencies")),
                descriptor(Path.of("..", "jfoundry-modules-dependencies")),
                descriptor(Path.of("..", "jfoundry-spring-boot-dependencies")),
                descriptor(Path.of("..", "jfoundry-spring-cloud-dependencies")),
                descriptor(Path.of("..", "jfoundry-quarkus-dependencies")),
                descriptor(Path.of("..", "jfoundry-helidon-dependencies")));
        String reactorVersion = projectVersion(descriptor(Path.of("..", "..")));
        String expectedLiteralTag = expectedLiteralScmTag(reactorVersion);

        for (Path pomPath : pomPaths) {
            assertThat(projectVersion(pomPath))
                    .as("project version for %s", pomPath)
                    .isEqualTo(reactorVersion);
            assertThat(scmTag(pomPath))
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
        Path foundation = descriptor(Path.of("..", "jfoundry-foundation-dependencies"));
        Path boot = descriptor(Path.of("..", "jfoundry-spring-boot-dependencies"));
        Path cloud = descriptor(Path.of("..", "jfoundry-spring-cloud-dependencies"));

        assertThat(managesDependency(foundation, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isFalse();
        assertThat(managesDependency(foundation, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isFalse();
        assertThat(managesDependency(foundation, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(managesDependency(foundation, "org.redisson", "redisson-spring-boot-starter")).isFalse();
        assertThat(managesDependency(foundation, "org.jmolecules.integrations", "jmolecules-spring")).isFalse();

        assertThat(managesDependency(boot, "org.jobrunr", "jobrunr-spring-boot-4-starter")).isTrue();
        assertThat(managesDependency(boot, "com.baomidou", "mybatis-plus-spring-boot4-starter")).isTrue();
        assertThat(managesDependency(boot, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(managesDependency(boot, "org.redisson", "redisson-spring-boot-starter")).isTrue();
        assertThat(managesDependency(boot, "org.jmolecules.integrations", "jmolecules-spring")).isTrue();

        assertThat(importedBoms(boot)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        assertThat(importedBoms(cloud)).doesNotContain(
                new Coordinate("io.github.xfoundries", "jfoundry-foundation-dependencies", "${project.version}"));
        for (Path springLine : List.of(boot, cloud)) {
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
    void cloudBomOwnsOnlyCloudPlatformVersions() throws Exception {
        Path descriptor = descriptor(Path.of("..", "jfoundry-spring-cloud-dependencies"));

        assertThat(property(descriptor, "spring-boot.version")).isNull();
        assertThat(importedBoms(descriptor)).containsExactly(
                new Coordinate("org.springframework.cloud", "spring-cloud-dependencies", "${spring-cloud.version}"),
                new Coordinate("com.alibaba.cloud", "spring-cloud-alibaba-dependencies", "${spring-cloud-alibaba.version}"));
    }

    @Test
    void quarkusRuntimeBuildMatchesTheConsumerBomPlatformVersion() throws Exception {
        Path runtime = descriptor(Path.of("..", "..", "jfoundry-runtime", "jfoundry-quarkus"));
        Path bom = descriptor(Path.of("..", "jfoundry-quarkus-dependencies"));
        String runtimeVersion = property(runtime, "quarkus.version");
        String bomVersion = property(bom, "quarkus.version");

        assertThat(runtimeVersion).as("Quarkus runtime and consumer BOM versions").isEqualTo(bomVersion);
        assertThat(bomVersion).isEqualTo("3.39.1");
        assertThat(importedBoms(runtime)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-quarkus-dependencies", "${project.version}"));
    }

    @Test
    void helidonRuntimeBuildUsesTheConsumerBomAsItsPlatformVersionSource() throws Exception {
        Path runtime = descriptor(Path.of("..", "..", "jfoundry-runtime", "jfoundry-helidon"));
        Path bom = descriptor(Path.of("..", "jfoundry-helidon-dependencies"));

        assertThat(property(runtime, "helidon.version")).isNull();
        assertThat(property(bom, "helidon.version")).isEqualTo("4.5.3");
        assertThat(importedBoms(runtime)).containsExactly(
                new Coordinate("io.github.xfoundries", "jfoundry-helidon-dependencies", "${project.version}"));
    }

    @Test
    void springAutoconfigurationUsesTheMybatisPlusStarterInsteadOfManagingMybatisSpring() throws Exception {
        Path autoconfigure = Path.of("..", "..", "jfoundry-runtime", "jfoundry-spring", "autoconfigure");
        Path persistence = descriptor(autoconfigure.resolve(
                "jfoundry-persistence-mybatis-plus-spring-boot-autoconfigure"));
        Path inbox = descriptor(autoconfigure.resolve("jfoundry-inbox-spring-boot-autoconfigure"));
        Path outbox = descriptor(autoconfigure.resolve("jfoundry-outbox-spring-boot-autoconfigure"));

        assertThat(hasDependency(persistence, "org.mybatis", "mybatis-spring")).isFalse();
        assertThat(dependencyValue(
                persistence, "com.baomidou", "mybatis-plus-spring-boot4-starter", "scope"))
                .isEqualTo("test");

        for (Path descriptor : List.of(inbox, outbox)) {
            assertThat(hasDependency(descriptor, "org.mybatis", "mybatis-spring")).isFalse();
            assertThat(dependencyValue(
                    descriptor, "com.baomidou", "mybatis-plus-spring-boot4-starter", "scope"))
                    .isEqualTo("provided");
            assertThat(dependencyValue(
                    descriptor, "com.baomidou", "mybatis-plus-spring-boot4-starter", "optional"))
                    .isEqualTo("true");
        }
    }

    private Document document(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yamlDocument(Path path) throws ReflectiveOperationException, IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            Class<?> yamlType = Class.forName("org.yaml.snakeyaml.Yaml");
            Object yaml = yamlType.getConstructor().newInstance();
            return (Map<String, Object>) yamlType.getMethod("load", Reader.class).invoke(yaml, reader);
        }
    }

    private Path descriptor(Path directory) {
        Path yaml = directory.resolve("pom.yaml");
        return Files.exists(yaml) ? yaml : directory.resolve("pom.xml");
    }

    private Coordinate coordinate(Path path, String name) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return coordinate(mapping(yamlDocument(path), name));
        }
        return coordinate(child(document(path).getDocumentElement(), name));
    }

    private String projectVersion(Path path) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return value(yamlDocument(path), "version");
        }
        return childText(document(path).getDocumentElement(), "version");
    }

    private String scmTag(Path path) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return value(mapping(yamlDocument(path), "scm"), "tag");
        }
        return childText(child(document(path).getDocumentElement(), "scm"), "tag");
    }

    private String property(Path path, String name) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return value(mapping(yamlDocument(path), "properties"), name);
        }
        return childText(child(document(path).getDocumentElement(), "properties"), name);
    }

    private List<Coordinate> importedBoms(Path path) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return importedBoms(yamlDocument(path));
        }
        return importedBoms(document(path));
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

    private List<Coordinate> importedBoms(Map<String, Object> document) {
        Map<String, Object> management = mapping(document, "dependencyManagement");
        if (management == null) {
            return List.of();
        }
        List<Coordinate> imports = new ArrayList<>();
        for (Map<String, Object> dependency : mappings(management, "dependencies")) {
            if ("import".equals(value(dependency, "scope"))) {
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

    private Coordinate coordinate(Map<String, Object> element) {
        return new Coordinate(
                value(element, "groupId"),
                value(element, "artifactId"),
                value(element, "version"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapping(Map<String, Object> parent, String name) {
        return (Map<String, Object>) parent.get(name);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mappings(Map<String, Object> parent, String name) {
        return (List<Map<String, Object>>) parent.get(name);
    }

    private String value(Map<String, Object> parent, String name) {
        if (parent == null) {
            return null;
        }
        Object value = parent.get(name);
        return value == null ? null : value.toString();
    }

    private boolean managesDependency(Path path, String groupId, String artifactId) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            Map<String, Object> management = mapping(yamlDocument(path), "dependencyManagement");
            return management != null && mappings(management, "dependencies").stream()
                    .anyMatch(candidate -> groupId.equals(value(candidate, "groupId"))
                            && artifactId.equals(value(candidate, "artifactId")));
        }
        return managesDependency(document(path), groupId, artifactId);
    }

    private boolean hasDependency(Path path, String groupId, String artifactId) throws Exception {
        return dependencyValue(path, groupId, artifactId, "artifactId") != null;
    }

    private String dependencyValue(Path path, String groupId, String artifactId, String name) throws Exception {
        if (path.getFileName().toString().endsWith(".yaml")) {
            return mappings(yamlDocument(path), "dependencies").stream()
                    .filter(candidate -> groupId.equals(value(candidate, "groupId")))
                    .filter(candidate -> artifactId.equals(value(candidate, "artifactId")))
                    .findFirst()
                    .map(candidate -> value(candidate, name))
                    .orElse(null);
        }
        Element dependency = dependency(document(path), groupId, artifactId);
        return dependency == null ? null : childText(dependency, name);
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
