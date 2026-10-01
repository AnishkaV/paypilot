package com.paypilot.payment.service;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("Payment " + paymentId + " not found");
    }
}
