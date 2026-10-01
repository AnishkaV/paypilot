package com.paypilot.payment.service;

public class RefundFailedException extends RuntimeException {

    public RefundFailedException(String paymentId, String code) {
        super("Refund for payment " + paymentId + " was not confirmed by the PSP (" + code + "). Safe to retry.");
    }
}
