package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.service;

import io.huahill.jfoundry.architecture.hexagonal.Application;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.in.CreateHelpDocumentUseCase;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.out.HelpDocumentReadModelPort;

@Application
public class HelpDocumentApplicationService implements CreateHelpDocumentUseCase {

    private final HelpDocumentReadModelPort readModelPort;

    public HelpDocumentApplicationService(HelpDocumentReadModelPort readModelPort) {
        this.readModelPort = readModelPort;
    }

    @Override
    public void create(String title) {
        readModelPort.findTitle();
    }
}
