package com.paypilot.payment.api;

import java.time.Instant;

import com.paypilot.payment.domain.PaymentAttempt;
import com.paypilot.payment.psp.PspOutcome;

public record AttemptResponse(String paymentId, String psp, PspOutcome outcome, String code, long latencyMs,
                              Instant at) {

    static AttemptResponse from(PaymentAttempt a) {
        return new AttemptResponse(a.getPaymentId(), a.getPsp(), a.getOutcome(), a.getCode(), a.getLatencyMs(),
                a.getCreatedAt());
    }
}
