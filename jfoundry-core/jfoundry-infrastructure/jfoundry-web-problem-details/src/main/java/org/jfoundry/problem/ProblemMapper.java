package org.jfoundry.problem;

import java.util.Optional;

/// Maps an exception to a runtime-neutral RFC 9457 problem descriptor.
@FunctionalInterface
public interface ProblemMapper {

    /// Returns a descriptor when this mapper owns the supplied exception.
    Optional<ProblemDescriptor> map(Exception exception);
}
