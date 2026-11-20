package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.adapter.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryAdapter;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.valid.port.out.HelpDocumentReadModelPort;

@SecondaryAdapter
public class MybatisHelpDocumentReadModelAdapter implements HelpDocumentReadModelPort {

    @Override
    public String findTitle() {
        return "title";
    }
}
