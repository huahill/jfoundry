import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VerifyConsumerPomXml {

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: VerifyConsumerPomXml <imported-artifact-ids|parent-coordinate|property-value> <pom> [property]");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new File(args[1]));
        Element project = document.getDocumentElement();

        switch (args[0]) {
            case "imported-artifact-ids" -> printImportedArtifactIds(project);
            case "parent-coordinate" -> printParentCoordinate(project);
            case "property-value" -> {
                if (args.length != 3) {
                    throw new IllegalArgumentException("property-value requires a property name");
                }
                printPropertyValue(project, args[2]);
            }
            default -> throw new IllegalArgumentException("Unsupported query: " + args[0]);
        }
    }

    private static void printImportedArtifactIds(Element project) {
        Element dependencyManagement = child(project, "dependencyManagement");
        Element dependencies = dependencyManagement == null ? null : child(dependencyManagement, "dependencies");
        if (dependencies == null) {
            return;
        }

        for (Node node = dependencies.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element dependency) || !hasName(dependency, "dependency")) {
                continue;
            }
            if ("pom".equals(text(dependency, "type")) && "import".equals(text(dependency, "scope"))) {
                System.out.print(text(dependency, "artifactId") + '\n');
            }
        }
    }

    private static void printParentCoordinate(Element project) {
        Element parent = child(project, "parent");
        if (parent == null) {
            return;
        }
        System.out.print(text(parent, "groupId") + ':' + text(parent, "artifactId") + ':' + text(parent, "version") + '\n');
    }

    private static void printPropertyValue(Element project, String property) {
        Element properties = child(project, "properties");
        if (properties != null) {
            System.out.print(text(properties, property) + '\n');
        }
    }

    private static Element child(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && hasName(element, name)) {
                return element;
            }
        }
        return null;
    }

    private static boolean hasName(Element element, String name) {
        return name.equals(element.getLocalName()) || name.equals(element.getNodeName());
    }

    private static String text(Element parent, String name) {
        Element child = child(parent, name);
        return child == null ? "" : child.getTextContent().trim();
    }
}
