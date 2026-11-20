package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.adapter;

import io.huahill.jfoundry.architecture.cqrs.CommandDispatcher;
import io.huahill.jfoundry.architecture.cqrs.CommandHandler;

class AdapterCommandHandlers {

    @CommandHandler
    void handle() {
    }

    @CommandDispatcher
    void dispatch() {
    }
}
