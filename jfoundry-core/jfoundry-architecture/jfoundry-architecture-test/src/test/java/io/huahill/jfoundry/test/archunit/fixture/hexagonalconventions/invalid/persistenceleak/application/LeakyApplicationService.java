package io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.persistenceleak.application;

import io.huahill.jfoundry.architecture.hexagonal.Application;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalconventions.invalid.persistenceleak.mybatis.mapper.HelpDocumentMapper;

@Application
public class LeakyApplicationService {

    private final HelpDocumentMapper mapper;

    public LeakyApplicationService(HelpDocumentMapper mapper) {
        this.mapper = mapper;
    }
}
