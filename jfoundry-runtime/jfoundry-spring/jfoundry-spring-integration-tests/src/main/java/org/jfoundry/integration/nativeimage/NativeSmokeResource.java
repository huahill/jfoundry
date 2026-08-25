package org.jfoundry.integration.nativeimage;

import java.util.concurrent.Callable;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/// HTTP endpoint used to verify that the Spring Native Image consumer has started.
@RestController
class NativeSmokeResource {

    @GetMapping("/jfoundry/native/ready")
    String ready() {
        return "ready";
    }

    @GetMapping("/jfoundry/native/failure")
    String failure() {
        throw new IllegalStateException("native smoke failure");
    }

    @GetMapping("/jfoundry/native/async")
    Callable<String> async() {
        return () -> {
            Thread.sleep(50);
            return "async-ready";
        };
    }
}
