package io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.query;

import io.huahill.jfoundry.architecture.cqrs.QueryModel;

@QueryModel
record HelpDocumentSummary(String title) {
}
