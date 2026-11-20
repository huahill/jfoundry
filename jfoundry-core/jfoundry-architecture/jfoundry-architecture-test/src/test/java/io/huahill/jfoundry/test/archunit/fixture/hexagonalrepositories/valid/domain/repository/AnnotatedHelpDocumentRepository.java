package io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid.domain.repository;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid.domain.model.HelpDocument;
import io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid.domain.model.HelpDocumentId;
import org.jmolecules.ddd.types.Repository;

@SecondaryPort
public interface AnnotatedHelpDocumentRepository extends Repository<HelpDocument, HelpDocumentId> {
}
