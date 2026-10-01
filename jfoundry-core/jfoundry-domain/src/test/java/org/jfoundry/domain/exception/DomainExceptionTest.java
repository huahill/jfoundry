package org.jfoundry.domain.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DomainExceptionTest {

    @Test
    void ruleViolationPreservesMessageAndCause() {
        RuntimeException cause = new RuntimeException("quota service returned stale data");

        DomainRuleViolationException exception = new DomainRuleViolationException("Quota exceeded", cause);

        assertInstanceOf(DomainException.class, exception);
        assertEquals("Quota exceeded", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void stateExceptionPreservesMessageAndCause() {
        IllegalStateException cause = new IllegalStateException("current state is RUNNING");

        DomainStateException exception = new DomainStateException("Cannot delete running environment", cause);

        assertInstanceOf(DomainException.class, exception);
        assertEquals("Cannot delete running environment", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void ruleViolationCarriesCodeAndArgumentsWithALocaleIndependentMessage() {
        DomainRuleViolationException exception = new DomainRuleViolationException("order.quota-exceeded", 2, 2);

        assertEquals("order.quota-exceeded [2, 2]", exception.getMessage());
        assertEquals(Optional.of("order.quota-exceeded"), exception.problemCode());
        assertEquals(List.of(2, 2), exception.problemArguments());
    }

    @Test
    void stateExceptionCarriesCodeWithoutLosingArguments() {
        DomainStateException exception = new DomainStateException("order.cannot-cancel-in-state", "order-1", "SHIPPED");

        assertEquals("order.cannot-cancel-in-state [order-1, SHIPPED]", exception.getMessage());
        assertEquals(Optional.of("order.cannot-cancel-in-state"), exception.problemCode());
        assertEquals(List.of("order-1", "SHIPPED"), exception.problemArguments());
    }

    @Test
    void literalMessageFailuresCarryNoCode() {
        DomainRuleViolationException exception = new DomainRuleViolationException("Quota exceeded");

        assertEquals(Optional.empty(), exception.problemCode());
        assertEquals(List.of(), exception.problemArguments());
    }

    @Test
    void rejectsBlankCodesAndUnsupportedArgumentTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new DomainRuleViolationException(" ", new Object[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new DomainRuleViolationException("order.quota-exceeded", new Object()));
        assertThrows(IllegalArgumentException.class,
                () -> new DomainRuleViolationException("order.quota-exceeded", (Object) null));
    }
}
