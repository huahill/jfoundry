package org.jfoundry.web.spring;

import org.jfoundry.problem.ProblemDescriptor;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailRendererTest {

    @Test
    void rendersRuntimeNeutralProblemDescriptor() {
        ProblemDescriptor descriptor = new ProblemDescriptor(URI.create("urn:test:problem"), "Test problem", 422,
                "A test problem occurred.", Map.of("retryable", false));

        var problemDetail = ProblemDetailRenderer.render(descriptor);

        assertThat(problemDetail.getStatus()).isEqualTo(422);
        assertThat(problemDetail.getType()).isEqualTo(descriptor.type());
        assertThat(problemDetail.getTitle()).isEqualTo(descriptor.title());
        assertThat(problemDetail.getDetail()).isEqualTo(descriptor.detail());
        assertThat(problemDetail.getProperties()).containsEntry("retryable", false);
    }
}
