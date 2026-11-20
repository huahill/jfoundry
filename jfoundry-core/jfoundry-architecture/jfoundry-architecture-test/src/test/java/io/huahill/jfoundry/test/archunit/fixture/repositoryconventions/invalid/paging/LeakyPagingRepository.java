package io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.invalid.paging;

import io.huahill.jfoundry.domain.repository.AggregateRepository;
import io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.valid.HelpDocument;
import io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.valid.HelpDocumentId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LeakyPagingRepository extends AggregateRepository<HelpDocument, HelpDocumentId> {

    Page<HelpDocument> page(Pageable pageable);
}
