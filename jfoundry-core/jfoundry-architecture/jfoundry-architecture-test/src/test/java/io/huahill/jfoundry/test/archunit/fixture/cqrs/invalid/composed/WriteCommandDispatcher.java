package io.huahill.jfoundry.test.archunit.fixture.cqrs.invalid.composed;

import io.huahill.jfoundry.architecture.cqrs.CommandDispatcher;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@CommandDispatcher
@Retention(RetentionPolicy.RUNTIME)
@interface WriteCommandDispatcher {
}
