package io.huahill.jfoundry.test.archunit.fixture.repositoryconventions.valid;

import org.jmolecules.ddd.types.AggregateRoot;

public class HelpDocument implements AggregateRoot<HelpDocument, HelpDocumentId> {

    @Override
    public HelpDocumentId getId() {
        return new HelpDocumentId("help-document-1");
    }
}
