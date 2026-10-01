package com.paypilot.payment.api;

import java.time.Instant;

import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentMethod;
import com.paypilot.payment.domain.PaymentStatus;

/** @param amount in paise (minor units) */
public record PaymentResponse(
        String id,
        String merchantId,
        PaymentStatus status,
        long amount,
        String currency,
        PaymentMethod method,
        String payer,
        String description,
        String psp,
        String pspReference,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getMerchantId(), p.getStatus(), p.getAmountMinor(), p.getCurrency(),
                p.getMethod(), p.getPayer(), p.getDescription(), p.getPsp(), p.getPspReference(),
                p.getFailureCode(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
