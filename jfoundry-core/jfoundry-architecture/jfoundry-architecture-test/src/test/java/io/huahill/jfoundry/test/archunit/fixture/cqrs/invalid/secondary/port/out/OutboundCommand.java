package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.secondary.port.out;

import io.huahill.jfoundry.architecture.cqrs.Command;

@Command
record OutboundCommand(String title) {
}
