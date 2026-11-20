package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.command;

import io.huahill.jfoundry.architecture.cqrs.Command;
import io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.infrastructure.InfrastructureDependency;

@Command
record InfrastructureAwareCommand(InfrastructureDependency dependency) {
}
