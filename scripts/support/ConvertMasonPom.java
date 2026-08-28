import eu.maveniverse.maven.mason.MasonParser;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.Sources;
import org.apache.maven.api.spi.ModelParser;
import org.apache.maven.model.v4.MavenStaxWriter;

public final class ConvertMasonPom {
    private ConvertMasonPom() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ConvertMasonPom <pom.yaml> <pom.xml>");
        }

        Path source = Path.of(args[0]);
        Path destination = Path.of(args[1]);
        Model model = new MasonParser().parse(
                Sources.fromPath(source), Map.of(ModelParser.STRICT, true));

        MavenStaxWriter writer = new MavenStaxWriter();
        writer.setAddLocationInformation(false);
        try (OutputStream output = Files.newOutputStream(destination)) {
            writer.write(output, model);
        }
    }
}
