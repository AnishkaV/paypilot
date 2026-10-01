package com.paypilot.payment.domain;

import java.time.Instant;

import com.paypilot.payment.psp.PspOutcome;
import com.paypilot.payment.psp.PspResult;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per PSP we tried (or skipped) for a payment. This is the audit trail that answers
 * "why did this payment fail?" and it is what the AI copilot will read in Phase 3.
 */
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String paymentId;
    private String psp;
    @Enumerated(EnumType.STRING)
    private PspOutcome outcome;
    private String code;
    private long latencyMs;
    private Instant createdAt;

    protected PaymentAttempt() {
        // for JPA
    }

    public static PaymentAttempt of(String paymentId, String psp, PspResult result) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.paymentId = paymentId;
        attempt.psp = psp;
        attempt.outcome = result.outcome();
        attempt.code = result.code();
        attempt.latencyMs = result.latencyMs();
        attempt.createdAt = Instant.now();
        return attempt;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getPsp() {
        return psp;
    }

    public PspOutcome getOutcome() {
        return outcome;
    }

    public String getCode() {
        return code;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
