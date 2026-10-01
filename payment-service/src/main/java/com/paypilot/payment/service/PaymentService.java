package com.paypilot.payment.service;

import java.util.List;
import java.util.Optional;

import com.paypilot.payment.domain.IllegalStateTransitionException;
import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentAttempt;
import com.paypilot.payment.domain.PaymentAttemptRepository;
import com.paypilot.payment.domain.PaymentMethod;
import com.paypilot.payment.domain.PaymentRepository;
import com.paypilot.payment.domain.PaymentStatus;
import com.paypilot.payment.idempotency.IdempotencyGuard;
import com.paypilot.payment.idempotency.IdempotencyKeyReusedException;
import com.paypilot.payment.idempotency.IdempotencyLock;
import com.paypilot.payment.idempotency.RequestFingerprint;
import com.paypilot.payment.psp.PspClient;
import com.paypilot.payment.psp.PspOutcome;
import com.paypilot.payment.psp.PspResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final IdempotencyGuard idempotencyGuard;
    private final PaymentStateWriter writer;
    private final PaymentProcessor processor;
    private final PspClient pspClient;

    public PaymentService(PaymentRepository payments, PaymentAttemptRepository attempts,
                          IdempotencyGuard idempotencyGuard, PaymentStateWriter writer,
                          PaymentProcessor processor, PspClient pspClient) {
        this.payments = payments;
        this.attempts = attempts;
        this.idempotencyGuard = idempotencyGuard;
        this.writer = writer;
        this.processor = processor;
        this.pspClient = pspClient;
    }

    /**
     * Creates and processes a payment. Calling it again with the same idempotency key returns the
     * first payment instead of charging again.
     */
    public PaymentOutcome create(String merchantId, String idempotencyKey, NewPayment request) {
        String fingerprint = RequestFingerprint.of(request.amountMinor(), request.currency(),
                request.method(), request.payer(), request.description());

        // 1. Seen this key before? Return the stored payment.
        Optional<Payment> existing = payments.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), fingerprint);
        }

        // 2. Block an identical request arriving at the same moment.
        try (IdempotencyLock lock = idempotencyGuard.acquire(merchantId, idempotencyKey)) {
            Payment payment;
            try {
                payment = writer.create(Payment.create(merchantId, idempotencyKey, fingerprint,
                        request.amountMinor(), request.currency(), request.method(),
                        request.payer(), request.description()));
            } catch (DataIntegrityViolationException e) {
                // 3. The unique constraint caught a duplicate the lock missed (e.g. Redis was down).
                Payment winner = payments.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                        .orElseThrow(() -> e);
                return replay(winner, fingerprint);
            }
            return new PaymentOutcome(processor.process(payment), false);
        }
    }

    public Payment get(String merchantId, String paymentId) {
        return payments.findById(paymentId)
                .filter(p -> p.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public List<Payment> recent(String merchantId, int limit) {
        return payments.findByMerchantIdOrderByCreatedAtDesc(merchantId, PageRequest.of(0, limit));
    }

    public List<PaymentAttempt> attempts(String merchantId, String paymentId) {
        get(merchantId, paymentId);
        return attempts.findByPaymentIdOrderByIdAsc(paymentId);
    }

    /** Full refund. Refunding an already refunded payment is a no-op, so retries are safe. */
    public Payment refund(String merchantId, String paymentId) {
        Payment payment = get(merchantId, paymentId);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateTransitionException(paymentId, payment.getStatus(), PaymentStatus.REFUNDED);
        }
        PspResult result = pspClient.refund(payment.getPsp(), paymentId);
        if (result.outcome() != PspOutcome.SUCCEEDED) {
            throw new RefundFailedException(paymentId, result.code());
        }
        return writer.refund(paymentId);
    }

    private static PaymentOutcome replay(Payment payment, String fingerprint) {
        if (!payment.getRequestHash().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(payment.getIdempotencyKey());
        }
        return new PaymentOutcome(payment, true);
    }

    public record NewPayment(long amountMinor, String currency, PaymentMethod method,
                             String payer, String description) {
    }

    /** @param replayed true when the payment came from an earlier request with the same key */
    public record PaymentOutcome(Payment payment, boolean replayed) {
    }
}
