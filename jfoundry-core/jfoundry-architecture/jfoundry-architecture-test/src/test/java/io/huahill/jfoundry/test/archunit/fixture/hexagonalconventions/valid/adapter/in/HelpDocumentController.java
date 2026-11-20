package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.adapter.in;

import io.huahill.jfoundry.architecture.hexagonal.PrimaryAdapter;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.in.CreateHelpDocumentUseCase;

@PrimaryAdapter
public class HelpDocumentController {

    private final CreateHelpDocumentUseCase useCase;

    public HelpDocumentController(CreateHelpDocumentUseCase useCase) {
        this.useCase = useCase;
    }

    void create() {
        useCase.create("title");
    }
}
