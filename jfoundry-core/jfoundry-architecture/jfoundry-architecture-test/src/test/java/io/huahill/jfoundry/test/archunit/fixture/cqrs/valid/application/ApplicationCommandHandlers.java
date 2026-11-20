package io.huahill.jfoundry.test.archunit.fixture.cqrs.valid.application;

import io.huahill.jfoundry.architecture.cqrs.CommandDispatcher;
import io.huahill.jfoundry.architecture.cqrs.CommandHandler;

class ApplicationCommandHandlers {

    @CommandHandler
    void handle() {
    }

    @CommandDispatcher
    void dispatch() {
    }
}
