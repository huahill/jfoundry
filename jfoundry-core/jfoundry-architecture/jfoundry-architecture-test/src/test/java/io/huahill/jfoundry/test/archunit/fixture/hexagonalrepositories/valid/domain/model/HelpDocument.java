package io.huahill.jfoundry.test.archunit.fixture.hexagonalrepositories.valid.domain.model;

import org.jmolecules.ddd.types.AggregateRoot;

public final class HelpDocument implements AggregateRoot<HelpDocument, HelpDocumentId> {

    private final HelpDocumentId id = new HelpDocumentId("help-document-1");

    @Override
    public HelpDocumentId getId() {
        return id;
    }
}
