package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.in;

import io.huahill.jfoundry.architecture.hexagonal.PrimaryPort;

@PrimaryPort
public interface CreateHelpDocumentUseCase {

    void create(String title);
}
