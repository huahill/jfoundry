package org.jfoundry.infrastructure.outbox.jobrunr.quarkus;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

/// Opens JobRunr SQL migrations on GraalVM 25.
///
/// JobRunr still mounts `resource:/resources`. GraalVM 25 rejects that URI and requires
/// `resource:/` for the default resource root. Closing the resulting filesystem would also
/// close the process-wide native resource filesystem, so this substitution leaves it open.
/// Remove this class when JobRunr itself opens `resource:/` on GraalVM 25.
@TargetClass(className = "org.jobrunr.utils.resources.ResourcesFileSystemProvider")
public final class JobRunrNativeSqlResourceSubstitution {

    @Alias
    private FileSystem fileSystem;

    @Substitute
    public Path toPath(URI uri) throws IOException {
        if (uri == null || !"resource".equals(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "ResourcesFileSystemProvider only supports uri's starting with resource:");
        }
        if (fileSystem == null) {
            URI root = URI.create("resource:/");
            try {
                fileSystem = FileSystems.getFileSystem(root);
            } catch (RuntimeException exception) {
                fileSystem = FileSystems.newFileSystem(root, Map.of("create", Boolean.TRUE), null);
            }
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "/";
        }
        return fileSystem.getPath(path);
    }

    @Substitute
    public void close() throws IOException {
        // The native resource filesystem is shared by the process.
    }
}
