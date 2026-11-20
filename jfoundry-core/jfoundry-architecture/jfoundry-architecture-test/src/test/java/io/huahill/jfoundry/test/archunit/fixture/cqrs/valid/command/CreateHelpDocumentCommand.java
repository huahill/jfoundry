package io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.command;

import io.huahill.jfoundry.architecture.cqrs.Command;

@Command
record CreateHelpDocumentCommand(String title) {
}
