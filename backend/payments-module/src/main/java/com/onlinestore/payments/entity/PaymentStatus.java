package com.onlinestore.payments.entity;

import com.onlinestore.common.exception.BusinessException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    AUTHORIZED,
    PAID,
    FAILED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS;

    static {
        var transitions = new EnumMap<PaymentStatus, Set<PaymentStatus>>(PaymentStatus.class);
        transitions.put(PENDING, EnumSet.of(REQUIRES_ACTION, AUTHORIZED, PAID, FAILED));
        transitions.put(REQUIRES_ACTION, EnumSet.of(AUTHORIZED, PAID, FAILED));
        transitions.put(AUTHORIZED, EnumSet.of(PAID, FAILED));
        transitions.put(PAID, EnumSet.of(REFUNDED));
        transitions.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        transitions.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        ALLOWED_TRANSITIONS = Map.copyOf(transitions);
    }

    public boolean canTransitionTo(PaymentStatus target) {
        Set<PaymentStatus> transitions = ALLOWED_TRANSITIONS.get(this);
        if (transitions == null) {
            return false;
        }
        return transitions.contains(target);
    }

    public void validateTransition(PaymentStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException(
                "INVALID_PAYMENT_TRANSITION",
                "Payment status transition from " + this + " to " + target + " is not allowed"
            );
        }
    }

    public boolean isTerminal() {
        Set<PaymentStatus> transitions = ALLOWED_TRANSITIONS.get(this);
        return transitions == null || transitions.isEmpty();
    }
}
