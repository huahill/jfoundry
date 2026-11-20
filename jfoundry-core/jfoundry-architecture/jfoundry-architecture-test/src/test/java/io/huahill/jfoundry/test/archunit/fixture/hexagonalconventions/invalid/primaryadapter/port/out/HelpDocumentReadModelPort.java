package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryadapter.port.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface HelpDocumentReadModelPort {

    String findTitle();
}
