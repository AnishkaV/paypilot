package com.paypilot.payment.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentStatus;
import com.paypilot.payment.outbox.OutboxEvent;
import com.paypilot.payment.outbox.OutboxRepository;
import com.paypilot.payment.routing.PspRouter;
import com.paypilot.payment.routing.PspRouter.PspHealth;
import com.paypilot.payment.service.OpsQueryService;
import com.paypilot.payment.service.OpsQueryService.OpsStats;
import com.paypilot.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational endpoints across all merchants. The gateway exposes them as /api/v1/ops/** to the
 * admin key only. In Phase 3 the AI copilot uses these as read-only tools.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal / Ops")
public class InternalController {

    private final PspRouter router;
    private final OutboxRepository outbox;
    private final OpsQueryService ops;
    private final PaymentService payments;

    public InternalController(PspRouter router, OutboxRepository outbox, OpsQueryService ops, PaymentService payments) {
        this.router = router;
        this.outbox = outbox;
        this.ops = ops;
        this.payments = payments;
    }

    @GetMapping("/stats")
    @Operation(summary = "Totals, success rate, per-PSP health and a 30 minute timeline")
    public OpsStats stats() {
        return ops.stats();
    }

    @GetMapping("/psps")
    @Operation(summary = "Circuit breaker state per PSP")
    public Map<String, PspHealth> pspHealth() {
        return router.health();
    }

    @GetMapping("/outbox")
    @Operation(summary = "How many events are waiting to be published")
    public Map<String, Long> outboxLag() {
        return Map.of("unpublished", outbox.countByPublishedAtIsNull());
    }

    @GetMapping("/payments")
    @Operation(summary = "Search payments across all merchants")
    public List<PaymentResponse> search(@RequestParam(required = false) PaymentStatus status,
                                        @RequestParam(required = false) String merchantId,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return ops.search(status, merchantId, q, limit).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "A payment with every PSP attempt and every event it produced")
    public PaymentDetail payment(@PathVariable String paymentId) {
        Payment payment = ops.payment(paymentId);
        return new PaymentDetail(PaymentResponse.from(payment),
                ops.attempts(paymentId).stream().map(AttemptResponse::from).toList(),
                ops.events(paymentId).stream().map(EventView::from).toList());
    }

    @PostMapping("/payments/{paymentId}/refund")
    @Operation(summary = "Refund on behalf of the merchant (support action)")
    public PaymentResponse refund(@PathVariable String paymentId) {
        Payment payment = ops.payment(paymentId);
        return PaymentResponse.from(payments.refund(payment.getMerchantId(), paymentId));
    }

    @GetMapping("/attempts/recent")
    @Operation(summary = "The latest PSP attempts, newest first (live feed)")
    public List<AttemptResponse> recentAttempts() {
        return ops.recentAttempts().stream().map(AttemptResponse::from).toList();
    }

    public record PaymentDetail(PaymentResponse payment, List<AttemptResponse> attempts, List<EventView> events) {
    }

    public record EventView(String eventId, String type, Instant createdAt, Instant publishedAt) {
        static EventView from(OutboxEvent e) {
            return new EventView(e.getEventId(), e.getEventType(), e.getCreatedAt(), e.getPublishedAt());
        }
    }
}
