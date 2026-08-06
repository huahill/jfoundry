package org.jfoundry.helidon.integration;

import jakarta.ws.rs.Produces;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class ProblemDetailsResourceTest {

    @Test
    void doesNotConstrainTheExceptionResponseMediaType() throws NoSuchMethodException {
        Produces produces = ProblemDetailsResource.class.getMethod("invalidArgument").getAnnotation(Produces.class);

        assertNull(produces);
    }
}
