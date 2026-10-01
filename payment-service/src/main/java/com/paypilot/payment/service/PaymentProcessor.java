package com.paypilot.payment.service;

import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.psp.ChargeCommand;
import com.paypilot.payment.psp.PspResult;
import com.paypilot.payment.routing.PspRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Walks the PSP list until one of them gives a final answer.
 *
 * <p>The failover rule: move to the next PSP only when we KNOW the current one did not charge
 * the customer (NOT_PROCESSED or circuit open). After a timeout (UNKNOWN) we stop and let the
 * sweeper ask the PSP later. Failing over after a timeout is how customers get charged twice.
 */
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    static final String NO_PSP_AVAILABLE = "NO_PSP_AVAILABLE";

    private final PspRouter router;
    private final PaymentStateWriter writer;

    public PaymentProcessor(PspRouter router, PaymentStateWriter writer) {
        this.router = router;
        this.writer = writer;
    }

    public Payment process(Payment payment) {
        String paymentId = payment.getId();
        ChargeCommand command = new ChargeCommand(paymentId, payment.getAmountMinor(),
                payment.getCurrency(), payment.getPayer());

        for (String psp : router.providers()) {
            if (!router.tryAcquire(psp)) {
                log.info("payment={} skipping psp={} (circuit open)", paymentId, psp);
                writer.recordOutcome(paymentId, psp, PspResult.circuitOpen());
                continue;
            }
            try {
                writer.startAttempt(paymentId, psp);
            } catch (RuntimeException e) {
                router.release(psp);
                throw e;
            }

            PspResult result = router.charge(psp, command);
            log.info("payment={} psp={} outcome={} code={} latencyMs={}",
                    paymentId, psp, result.outcome(), result.code(), result.latencyMs());
            Payment updated = writer.recordOutcome(paymentId, psp, result);

            switch (result.outcome()) {
                case SUCCEEDED, DECLINED -> {
                    return updated;
                }
                case UNKNOWN -> {
                    log.warn("payment={} outcome unknown at psp={}, leaving it PROCESSING for the sweeper",
                            paymentId, psp);
                    return updated;
                }
                case NOT_PROCESSED, CIRCUIT_OPEN -> {
                    // Safe: this PSP did not charge the customer. Try the next one.
                }
            }
        }
        log.warn("payment={} failed, no PSP could process it", paymentId);
        return writer.fail(paymentId, NO_PSP_AVAILABLE);
    }
}
