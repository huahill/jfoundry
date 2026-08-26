import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Verifies Maven dependency ownership against JFoundry module boundaries. */
public final class VerifyDependencyBoundaries {

    private VerifyDependencyBoundaries() {
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                    .filter(path -> !containsExcludedDirectory(root.relativize(path)))
                    .forEach(path -> inspect(root, path, violations));
        }

        if (!violations.isEmpty()) {
            violations.forEach(System.err::println);
            System.err.println("Dependency boundary check failed with " + violations.size() + " violation(s).");
            System.exit(1);
        }
    }

    private static void inspect(Path root, Path pom, List<String> violations) {
        try {
            Document document = parse(pom);
            ModuleKind module = classify(root.relativize(pom.getParent()));
            for (Element dependencies : elements(document.getDocumentElement(), "dependencies")) {
                Element parent = parentElement(dependencies);
                if (parent != null && "plugin".equals(localName(parent))) {
                    continue;
                }
                String context = hasAncestor(dependencies, "dependencyManagement")
                        ? "dependencyManagement"
                        : "dependencies";
                for (Element dependency : childElements(dependencies, "dependency")) {
                    String groupId = text(dependency, "groupId");
                    String artifactId = text(dependency, "artifactId");
                    if (groupId.isBlank() || artifactId.isBlank()) {
                        continue;
                    }
                    String scope = text(dependency, "scope");
                    check(module, groupId, artifactId, context + (scope.isBlank() ? "" : ":" + scope),
                            root.relativize(pom), violations);
                }
            }
        } catch (Exception exception) {
            violations.add(root.relativize(pom) + " [parse-error] " + exception.getMessage());
        }
    }

    private static void check(ModuleKind module, String groupId, String artifactId, String context, Path pom,
            List<String> violations) {
        String coordinate = (groupId + ":" + artifactId).toLowerCase(Locale.ROOT);
        boolean spring = isSpring(coordinate);
        boolean quarkus = isQuarkus(coordinate);
        boolean helidon = isHelidon(coordinate);

        if (module == ModuleKind.CORE && (spring || quarkus || helidon) && !isAllowedCoreApi(groupId, artifactId)) {
            violations.add(format(pom, coordinate, context, "core-runtime-dependency"));
        }
        if (module == ModuleKind.FOUNDATION_BOM && (spring || quarkus || helidon || isRuntimeMarker(coordinate))) {
            violations.add(format(pom, coordinate, context, "foundation-runtime-coordinate"));
        }
        if (module == ModuleKind.JAKARTA && (spring || quarkus || helidon)) {
            violations.add(format(pom, coordinate, context, "jakarta-cross-runtime-dependency"));
        }
        if ((module == ModuleKind.SPRING || module == ModuleKind.SPRING_BOM) && (quarkus || helidon)) {
            violations.add(format(pom, coordinate, context, "spring-cross-runtime-dependency"));
        }
        if ((module == ModuleKind.QUARKUS || module == ModuleKind.QUARKUS_BOM) && (spring || helidon)) {
            violations.add(format(pom, coordinate, context, "quarkus-cross-runtime-dependency"));
        }
        if ((module == ModuleKind.HELIDON || module == ModuleKind.HELIDON_BOM) && (spring || quarkus)) {
            violations.add(format(pom, coordinate, context, "helidon-cross-runtime-dependency"));
        }
        if (isRuntimeBom(module) && isDisallowedRuntimeBomImport(coordinate)) {
            violations.add(format(pom, coordinate, context, "runtime-bom-import"));
        }
    }

    private static String format(Path pom, String coordinate, String context, String rule) {
        return pom + " [" + rule + "] " + coordinate + " (" + context + ")";
    }

    private static boolean isAllowedCoreApi(String groupId, String artifactId) {
        return groupId.equals("jakarta.persistence") && artifactId.equals("jakarta.persistence-api");
    }

    private static boolean isSpring(String coordinate) {
        return coordinate.startsWith("org.springframework:")
                || coordinate.startsWith("org.springframework.")
                || coordinate.contains(":spring-")
                || coordinate.contains("-spring-")
                || coordinate.contains(":jmolecules-spring")
                || coordinate.contains(":jfoundry-" ) && coordinate.contains("-spring");
    }

    private static boolean isQuarkus(String coordinate) {
        return coordinate.startsWith("io.quarkus:")
                || coordinate.startsWith("io.quarkus.")
                || coordinate.contains(":jfoundry-" ) && coordinate.contains("-quarkus");
    }

    private static boolean isHelidon(String coordinate) {
        return coordinate.startsWith("io.helidon:")
                || coordinate.startsWith("io.helidon.")
                || coordinate.contains(":jfoundry-" ) && coordinate.contains("-helidon");
    }

    private static boolean isRuntimeMarker(String coordinate) {
        return coordinate.contains("-deployment") || coordinate.contains("-starter");
    }

    private static boolean isRuntimeBom(ModuleKind module) {
        return module == ModuleKind.SPRING_BOM
                || module == ModuleKind.QUARKUS_BOM
                || module == ModuleKind.HELIDON_BOM;
    }

    private static boolean isDisallowedRuntimeBomImport(String coordinate) {
        return coordinate.equals("io.github.xfoundries:jfoundry-dependencies")
                || coordinate.equals("io.github.xfoundries:jfoundry-foundation-dependencies")
                || coordinate.equals("io.github.xfoundries:jfoundry-spring-boot-dependencies")
                || coordinate.equals("io.github.xfoundries:jfoundry-spring-cloud-dependencies")
                || coordinate.equals("io.github.xfoundries:jfoundry-quarkus-dependencies")
                || coordinate.equals("io.github.xfoundries:jfoundry-helidon-dependencies");
    }

    private static ModuleKind classify(Path relativePath) {
        String path = relativePath.toString().replace('\\', '/');
        if (path.startsWith("jfoundry-core/")) {
            return ModuleKind.CORE;
        }
        if (path.startsWith("jfoundry-boms/jfoundry-spring-") && path.endsWith("-dependencies")) {
            return ModuleKind.SPRING_BOM;
        }
        if (path.startsWith("jfoundry-boms/jfoundry-quarkus-dependencies")) {
            return ModuleKind.QUARKUS_BOM;
        }
        if (path.startsWith("jfoundry-boms/jfoundry-helidon-dependencies")) {
            return ModuleKind.HELIDON_BOM;
        }
        if (path.startsWith("jfoundry-runtime/jfoundry-spring/")) {
            return ModuleKind.SPRING;
        }
        if (path.startsWith("jfoundry-runtime/jfoundry-jakarta/")) {
            return ModuleKind.JAKARTA;
        }
        if (path.startsWith("jfoundry-runtime/jfoundry-quarkus/")) {
            return ModuleKind.QUARKUS;
        }
        if (path.startsWith("jfoundry-runtime/jfoundry-helidon/")) {
            return ModuleKind.HELIDON;
        }
        if (path.startsWith("jfoundry-boms/jfoundry-foundation-dependencies")) {
            return ModuleKind.FOUNDATION_BOM;
        }
        return ModuleKind.OTHER;
    }

    private static boolean containsExcludedDirectory(Path relativePath) {
        for (Path part : relativePath) {
            if (part.toString().equals("target") || part.toString().equals(".git") || part.toString().equals(".worktrees")) {
                return true;
            }
        }
        return false;
    }

    private static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static List<Element> elements(Element root, String name) {
        List<Element> result = new ArrayList<>();
        collect(root, name, result);
        return result;
    }

    private static void collect(Element element, String name, List<Element> result) {
        if (name.equals(localName(element))) {
            result.add(element);
        }
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) {
                collect(child, name, result);
            }
        }
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && name.equals(localName(child))) {
                result.add(child);
            }
        }
        return result;
    }

    private static Element parentElement(Element element) {
        return element.getParentNode() instanceof Element parent ? parent : null;
    }

    private static boolean hasAncestor(Element element, String name) {
        Node node = element.getParentNode();
        while (node instanceof Element parent) {
            if (name.equals(localName(parent))) {
                return true;
            }
            node = parent.getParentNode();
        }
        return false;
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private static String text(Element parent, String name) {
        for (Element child : childElements(parent, name)) {
            return child.getTextContent().trim();
        }
        return "";
    }

    private enum ModuleKind {
        CORE, FOUNDATION_BOM, JAKARTA, SPRING, QUARKUS, HELIDON,
        SPRING_BOM, QUARKUS_BOM, HELIDON_BOM, OTHER
    }
}
