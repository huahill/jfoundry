package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface HelpDocumentReadModelPort {

    String findTitle();
}
