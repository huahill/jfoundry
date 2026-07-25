package org.jfoundry.integration.nativeimage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// HTTP endpoint used to verify that the Spring Native Image consumer has started.
@RestController
class NativeSmokeResource {

    @GetMapping("/jfoundry/native/ready")
    String ready() {
        return "ready";
    }
}
