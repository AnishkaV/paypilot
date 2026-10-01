package com.paypilot.payment.domain;

public class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(String paymentId, PaymentStatus from, PaymentStatus to) {
        super("Payment " + paymentId + " cannot move from " + from + " to " + to);
    }
}
