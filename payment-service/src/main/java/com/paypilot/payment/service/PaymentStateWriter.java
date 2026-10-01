package com.paypilot.payment.service;

import com.paypilot.common.events.PaymentEventType;
import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentAttempt;
import com.paypilot.payment.domain.PaymentAttemptRepository;
import com.paypilot.payment.domain.PaymentRepository;
import com.paypilot.payment.outbox.OutboxWriter;
import com.paypilot.payment.psp.PspResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every database write for a payment, each in its own short transaction.
 *
 * <p>Short on purpose: PSP calls happen between these methods, never inside them. Holding a
 * database transaction (and a connection from the pool) open while waiting 2 seconds on a
 * network call is how payment services run out of connections under load.
 */
@Component
public class PaymentStateWriter {

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final OutboxWriter outbox;

    public PaymentStateWriter(PaymentRepository payments, PaymentAttemptRepository attempts, OutboxWriter outbox) {
        this.payments = payments;
        this.attempts = attempts;
        this.outbox = outbox;
    }

    /** Throws DataIntegrityViolationException if the merchant already used this idempotency key. */
    @Transactional
    public Payment create(Payment payment) {
        payments.saveAndFlush(payment);
        outbox.append(payment, PaymentEventType.PAYMENT_CREATED);
        return payment;
    }

    @Transactional
    public Payment startAttempt(String paymentId, String psp) {
        Payment payment = load(paymentId);
        payment.startAttempt(psp);
        return payment;
    }

    /** Records the attempt and, for a final answer from the PSP, moves the payment to its end state. */
    @Transactional
    public Payment recordOutcome(String paymentId, String psp, PspResult result) {
        Payment payment = load(paymentId);
        attempts.save(PaymentAttempt.of(paymentId, psp, result));
        switch (result.outcome()) {
            case SUCCEEDED -> {
                payment.succeed(result.pspReference());
                outbox.append(payment, PaymentEventType.PAYMENT_SUCCEEDED);
            }
            case DECLINED -> {
                payment.fail(result.code());
                outbox.append(payment, PaymentEventType.PAYMENT_FAILED);
            }
            case NOT_PROCESSED, UNKNOWN, CIRCUIT_OPEN -> {
                // Status stays as is. The caller decides whether to try the next PSP.
            }
        }
        return payment;
    }

    /** Applies the answer to a status check for a payment whose outcome was unknown. */
    @Transactional
    public Payment resolveUnknown(String paymentId, PspResult statusCheck) {
        Payment payment = load(paymentId);
        switch (statusCheck.outcome()) {
            case SUCCEEDED -> {
                payment.succeed(statusCheck.pspReference());
                outbox.append(payment, PaymentEventType.PAYMENT_SUCCEEDED);
            }
            case DECLINED -> {
                payment.fail(statusCheck.code());
                outbox.append(payment, PaymentEventType.PAYMENT_FAILED);
            }
            case NOT_PROCESSED -> {
                // The PSP has no record of the charge, so the customer was never charged.
                payment.fail("PSP_TIMEOUT_NOT_CHARGED");
                outbox.append(payment, PaymentEventType.PAYMENT_FAILED);
            }
            case UNKNOWN, CIRCUIT_OPEN -> {
                // Still can't tell. Try again on the next sweep.
            }
        }
        return payment;
    }

    @Transactional
    public Payment fail(String paymentId, String failureCode) {
        Payment payment = load(paymentId);
        payment.fail(failureCode);
        outbox.append(payment, PaymentEventType.PAYMENT_FAILED);
        return payment;
    }

    @Transactional
    public Payment refund(String paymentId) {
        Payment payment = load(paymentId);
        payment.refund();
        outbox.append(payment, PaymentEventType.PAYMENT_REFUNDED);
        return payment;
    }

    private Payment load(String paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
