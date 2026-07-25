package org.jfoundry.integration.nativeimage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Minimal consumer application used to verify Spring Boot AOT and Native Image assembly.
@SpringBootApplication
public class NativeSmokeApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeSmokeApplication.class, args);
    }
}
