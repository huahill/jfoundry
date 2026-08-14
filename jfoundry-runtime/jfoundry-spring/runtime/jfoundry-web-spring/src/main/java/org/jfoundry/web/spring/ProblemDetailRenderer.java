package org.jfoundry.web.spring;

import org.jfoundry.problem.ProblemDescriptor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.util.Objects;

/// Renders runtime-neutral problem descriptors as Spring Problem Details.
public final class ProblemDetailRenderer {

    private ProblemDetailRenderer() {
    }

    public static ProblemDetail render(ProblemDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(descriptor.status()),
                descriptor.detail());
        problemDetail.setTitle(descriptor.title());
        problemDetail.setType(descriptor.type());
        descriptor.extensions().forEach(problemDetail::setProperty);
        return problemDetail;
    }
}
