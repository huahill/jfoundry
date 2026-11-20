package io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.invalid.persistenceservice;

import com.baomidou.mybatisplus.extension.service.IService;
import io.huahill.jfoundry.domain.repository.AggregateRepository;
import io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.valid.HelpDocument;
import io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.valid.HelpDocumentId;

public interface LeakyPersistenceServiceRepository extends AggregateRepository<HelpDocument, HelpDocumentId> {

    IService<HelpDocument> service();
}
