package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary.port.out;

import io.huahill.jfoundry.architecture.hexagonal.SecondaryPort;

import java.util.List;

@SecondaryPort
interface QueryModelReturningPort {

    List<OutboundQueryModel> findSummaries();
}
