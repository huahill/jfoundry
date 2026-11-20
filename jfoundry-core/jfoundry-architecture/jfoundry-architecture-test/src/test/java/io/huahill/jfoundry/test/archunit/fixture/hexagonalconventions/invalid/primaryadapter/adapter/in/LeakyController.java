package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryadapter.adapter.in;

import io.huahill.jfoundry.architecture.hexagonal.PrimaryAdapter;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.primaryadapter.port.out.HelpDocumentReadModelPort;

@PrimaryAdapter
public class LeakyController {

    private final HelpDocumentReadModelPort readModelPort;

    public LeakyController(HelpDocumentReadModelPort readModelPort) {
        this.readModelPort = readModelPort;
    }

    String title() {
        return readModelPort.findTitle();
    }
}
