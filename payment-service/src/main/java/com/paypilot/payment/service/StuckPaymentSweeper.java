package com.paypilot.payment.service;

import java.time.Instant;
import java.util.List;

import com.paypilot.payment.config.PayPilotProperties;
import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentRepository;
import com.paypilot.payment.domain.PaymentStatus;
import com.paypilot.payment.psp.PspClient;
import com.paypilot.payment.psp.PspResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resolves payments left in PROCESSING after a PSP timeout by asking the PSP what happened.
 *
 * <p>With several instances running, two sweepers may pick the same payment. The @Version column
 * on Payment makes the second update fail instead of overwriting the first, and we just skip it.
 */
@Component
public class StuckPaymentSweeper {

    private static final Logger log = LoggerFactory.getLogger(StuckPaymentSweeper.class);

    private final PaymentRepository payments;
    private final PspClient pspClient;
    private final PaymentStateWriter writer;
    private final PayPilotProperties.Sweeper config;

    public StuckPaymentSweeper(PaymentRepository payments, PspClient pspClient,
                               PaymentStateWriter writer, PayPilotProperties properties) {
        this.payments = payments;
        this.pspClient = pspClient;
        this.writer = writer;
        this.config = properties.sweeper();
    }

    @Scheduled(fixedDelayString = "${paypilot.sweeper.interval-ms:10000}",
            initialDelayString = "${paypilot.sweeper.interval-ms:10000}")
    public void resolveStuckPayments() {
        Instant cutoff = Instant.now().minus(config.stuckAfter());
        List<Payment> stuck = payments.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff,
                PageRequest.of(0, config.batchSize()));
        stuck.forEach(this::resolve);
    }

    void resolve(Payment payment) {
        PspResult statusCheck = pspClient.getCharge(payment.getPsp(), payment.getId());
        try {
            Payment resolved = writer.resolveUnknown(payment.getId(), statusCheck);
            log.info("sweeper payment={} psp={} status check={} -> {}", payment.getId(), payment.getPsp(),
                    statusCheck.outcome(), resolved.getStatus());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("sweeper payment={} was updated concurrently, skipping", payment.getId());
        }
    }
}
