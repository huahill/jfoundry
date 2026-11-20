package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.domain;

import io.huahill.jfoundry.architecture.cqrs.QueryModel;

@QueryModel
record DomainQueryModel(String title) {
}
