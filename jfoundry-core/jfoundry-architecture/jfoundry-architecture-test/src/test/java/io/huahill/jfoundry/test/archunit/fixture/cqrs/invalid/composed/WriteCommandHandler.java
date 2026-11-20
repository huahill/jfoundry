package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.composed;

import io.huahill.jfoundry.architecture.cqrs.CommandHandler;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@CommandHandler
@Retention(RetentionPolicy.RUNTIME)
@interface WriteCommandHandler {
}
