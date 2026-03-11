package com.onlinestore.payments.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onlinestore.common.exception.BusinessException;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PaymentStatusTest {

    @Test
    void pendingShouldAllowForwardTransitions() {
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REQUIRES_ACTION));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.AUTHORIZED));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PAID));
        assertTrue(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void pendingShouldRejectRefunded() {
        assertFalse(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REFUNDED));
    }

    @Test
    void requiresActionShouldAllowForwardTransitions() {
        assertTrue(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.AUTHORIZED));
        assertTrue(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.PAID));
        assertTrue(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void requiresActionShouldRejectBackwardAndRefund() {
        assertFalse(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.REQUIRES_ACTION.canTransitionTo(PaymentStatus.REFUNDED));
    }

    @Test
    void authorizedShouldAllowPaidAndFailed() {
        assertTrue(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.PAID));
        assertTrue(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void authorizedShouldRejectBackwardAndRefund() {
        assertFalse(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.REQUIRES_ACTION));
        assertFalse(PaymentStatus.AUTHORIZED.canTransitionTo(PaymentStatus.REFUNDED));
    }

    @Test
    void paidShouldAllowOnlyRefunded() {
        assertTrue(PaymentStatus.PAID.canTransitionTo(PaymentStatus.REFUNDED));
    }

    @Test
    void paidShouldRejectAllOtherTransitions() {
        assertFalse(PaymentStatus.PAID.canTransitionTo(PaymentStatus.PENDING));
        assertFalse(PaymentStatus.PAID.canTransitionTo(PaymentStatus.REQUIRES_ACTION));
        assertFalse(PaymentStatus.PAID.canTransitionTo(PaymentStatus.AUTHORIZED));
        assertFalse(PaymentStatus.PAID.canTransitionTo(PaymentStatus.FAILED));
    }

    @Test
    void failedShouldRejectAllTransitions() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertFalse(PaymentStatus.FAILED.canTransitionTo(target),
                "FAILED should not transition to " + target);
        }
    }

    @Test
    void refundedShouldRejectAllTransitions() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertFalse(PaymentStatus.REFUNDED.canTransitionTo(target),
                "REFUNDED should not transition to " + target);
        }
    }

    @Test
    void terminalStatusesShouldBeFailedAndRefunded() {
        assertTrue(PaymentStatus.FAILED.isTerminal());
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
        assertFalse(PaymentStatus.PENDING.isTerminal());
        assertFalse(PaymentStatus.REQUIRES_ACTION.isTerminal());
        assertFalse(PaymentStatus.AUTHORIZED.isTerminal());
        assertFalse(PaymentStatus.PAID.isTerminal());
    }

    @Test
    void validateTransitionShouldThrowOnInvalidTransition() {
        var ex = assertThrows(BusinessException.class,
            () -> PaymentStatus.PAID.validateTransition(PaymentStatus.FAILED));
        assertEquals("INVALID_PAYMENT_TRANSITION", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("PAID"));
        assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    void validateTransitionShouldPassForValidTransition() {
        assertDoesNotThrow(() -> PaymentStatus.PENDING.validateTransition(PaymentStatus.PAID));
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void noStatusShouldTransitionToItself(PaymentStatus status) {
        assertFalse(status.canTransitionTo(status),
            status + " should not allow self-transition");
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void everyStatusMustBeRegisteredInTransitionMatrix(PaymentStatus status) {
        // Ensures new enum values are explicitly added to the transition matrix
        assertDoesNotThrow(() -> status.isTerminal(),
            status + " must be registered in the transition matrix");
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void everyNonTerminalStatusShouldHaveAtLeastOneTransition(PaymentStatus status) {
        if (status.isTerminal()) {
            return;
        }
        Set<PaymentStatus> reachable = EnumSet.noneOf(PaymentStatus.class);
        for (PaymentStatus target : PaymentStatus.values()) {
            if (status.canTransitionTo(target)) {
                reachable.add(target);
            }
        }
        assertFalse(reachable.isEmpty(),
            status + " must have at least one allowed transition");
    }
}
