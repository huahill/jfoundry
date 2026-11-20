package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary.port.out;

import io.huahill.jfoundry.architecture.cqrs.QueryModel;

@QueryModel
record OutboundQueryModel(String title) {
}
