package org.jfoundry.test.archunit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventOutboxModuleBoundaryTest {

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "<dependency>\\s*<groupId>[^<]+</groupId>\\s*<artifactId>([^<]+)</artifactId>",
            Pattern.DOTALL);

    @Test
    void domainEventOutboxMappingIsSeparatedFromGenericOutbox() throws IOException {
        List<String> outboxDependencies = dependenciesOf("jfoundry-outbox-core");
        List<String> domainEventOutboxDependencies = dependenciesOf("jfoundry-domain-event-outbox-core");
        Path repositoryRoot = repositoryRoot();

        assertThat(outboxDependencies)
                .doesNotContain("jfoundry-domain-event-core")
                .noneMatch(artifactId -> artifactId.contains("domain-event-externalization"));
        assertThat(domainEventOutboxDependencies)
                .contains("jfoundry-domain-event-core", "jfoundry-outbox-core", "jfoundry-messaging-core");
        assertThat(repositoryRoot.resolve(Path.of(
                "jfoundry-core", "jfoundry-application",
                "jfoundry-domain-event-externalization-core", "pom.xml")))
                .doesNotExist();
    }

    private static List<String> dependenciesOf(String artifactId) throws IOException {
        Path repositoryRoot = repositoryRoot();
        Path pom = repositoryRoot.resolve(Path.of(
                "jfoundry-core", "jfoundry-application", artifactId, "pom.xml"));
        assertThat(pom).as("POM for %s", artifactId).isRegularFile();
        String pomXml = Files.readString(pom);
        return DEPENDENCY_PATTERN.matcher(pomXml).results()
                .map(match -> match.group(1))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("jfoundry-core"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate the repository root from "
                + System.getProperty("user.dir"));
    }
}
