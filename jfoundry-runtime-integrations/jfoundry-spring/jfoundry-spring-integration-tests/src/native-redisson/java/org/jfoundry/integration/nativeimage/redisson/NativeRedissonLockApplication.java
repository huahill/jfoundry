package org.jfoundry.integration.nativeimage.redisson;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Native Image consumer used to certify the JFoundry Redisson lock starter.
@SpringBootApplication
public class NativeRedissonLockApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeRedissonLockApplication.class, args);
    }
}
