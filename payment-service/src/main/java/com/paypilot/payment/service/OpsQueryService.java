package com.paypilot.payment.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.paypilot.payment.domain.Payment;
import com.paypilot.payment.domain.PaymentAttempt;
import com.paypilot.payment.domain.PaymentAttemptRepository;
import com.paypilot.payment.domain.PaymentAttemptRepository.PspOutcomeTotal;
import com.paypilot.payment.domain.PaymentRepository;
import com.paypilot.payment.domain.PaymentStatus;
import com.paypilot.payment.outbox.OutboxEvent;
import com.paypilot.payment.outbox.OutboxPublisher;
import com.paypilot.payment.outbox.OutboxRepository;
import com.paypilot.payment.psp.PspOutcome;
import com.paypilot.payment.routing.PspRouter;
import com.paypilot.payment.routing.PspRouter.PspHealth;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model for the ops dashboard: numbers across all merchants. Merchants never reach this;
 * the gateway only routes it for the admin key.
 */
@Service
@Transactional(readOnly = true)
public class OpsQueryService {

    private static final Duration TIMELINE_WINDOW = Duration.ofMinutes(30);

    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final OutboxRepository outbox;
    private final OutboxPublisher publisher;
    private final PspRouter router;

    public OpsQueryService(PaymentRepository payments, PaymentAttemptRepository attempts,
                           OutboxRepository outbox, OutboxPublisher publisher, PspRouter router) {
        this.payments = payments;
        this.attempts = attempts;
        this.outbox = outbox;
        this.publisher = publisher;
        this.router = router;
    }

    public OpsStats stats() {
        return new OpsStats(totals(), pspStats(), timeline(), publisher.transportName(),
                outbox.countByPublishedAtIsNull(), Instant.now());
    }

    public List<Payment> search(PaymentStatus status, String merchantId, String query, int limit) {
        Specification<Payment> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (merchantId != null && !merchantId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("merchantId"), merchantId));
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("id")), like),
                    cb.like(cb.lower(root.get("payer")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }
        return payments.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    public Payment payment(String paymentId) {
        return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public List<PaymentAttempt> attempts(String paymentId) {
        return attempts.findByPaymentIdOrderByIdAsc(paymentId);
    }

    public List<OutboxEvent> events(String paymentId) {
        return outbox.findByAggregateIdOrderByIdAsc(paymentId);
    }

    public List<PaymentAttempt> recentAttempts() {
        return attempts.findTop50ByOrderByIdDesc();
    }

    private Totals totals() {
        Map<PaymentStatus, long[]> byStatus = new EnumMap<>(PaymentStatus.class);
        for (PaymentRepository.StatusTotal row : payments.totalsByStatus()) {
            byStatus.put(row.getStatus(), new long[]{row.getCount(), row.getAmount()});
        }
        long succeeded = count(byStatus, PaymentStatus.SUCCEEDED);
        long refunded = count(byStatus, PaymentStatus.REFUNDED);
        long failed = count(byStatus, PaymentStatus.FAILED);
        long inFlight = count(byStatus, PaymentStatus.PROCESSING) + count(byStatus, PaymentStatus.CREATED);
        long total = succeeded + refunded + failed + inFlight;
        long completed = succeeded + refunded + failed;
        double successRate = completed == 0 ? 0 : 100.0 * (succeeded + refunded) / completed;
        long grossVolume = amount(byStatus, PaymentStatus.SUCCEEDED) + amount(byStatus, PaymentStatus.REFUNDED);

        long latencyWeight = 0;
        long latencyCount = 0;
        for (PspOutcomeTotal row : attempts.totalsByPspAndOutcome()) {
            if (row.getOutcome() == PspOutcome.SUCCEEDED || row.getOutcome() == PspOutcome.DECLINED) {
                latencyWeight += Math.round(row.getAvgLatency() * row.getCount());
                latencyCount += row.getCount();
            }
        }
        long avgLatency = latencyCount == 0 ? 0 : latencyWeight / latencyCount;
        return new Totals(total, succeeded, failed, inFlight, refunded, grossVolume,
                amount(byStatus, PaymentStatus.REFUNDED), Math.round(successRate * 10) / 10.0, avgLatency);
    }

    private List<PspStats> pspStats() {
        Map<String, Map<PspOutcome, PspOutcomeTotal>> byPsp = new TreeMap<>();
        for (PspOutcomeTotal row : attempts.totalsByPspAndOutcome()) {
            byPsp.computeIfAbsent(row.getPsp(), p -> new EnumMap<>(PspOutcome.class)).put(row.getOutcome(), row);
        }
        Map<String, PspHealth> health = router.health();
        List<PspStats> result = new ArrayList<>();
        for (String psp : router.providers()) {
            Map<PspOutcome, PspOutcomeTotal> rows = byPsp.getOrDefault(psp, Map.of());
            long answered = n(rows, PspOutcome.SUCCEEDED) + n(rows, PspOutcome.DECLINED);
            double latencySum = 0;
            for (PspOutcome o : List.of(PspOutcome.SUCCEEDED, PspOutcome.DECLINED)) {
                PspOutcomeTotal row = rows.get(o);
                if (row != null) {
                    latencySum += row.getAvgLatency() * row.getCount();
                }
            }
            long calls = answered + n(rows, PspOutcome.NOT_PROCESSED) + n(rows, PspOutcome.UNKNOWN);
            PspHealth h = health.get(psp);
            result.add(new PspStats(psp, h.circuitState(), h.failureRate(), calls,
                    n(rows, PspOutcome.SUCCEEDED), n(rows, PspOutcome.DECLINED), n(rows, PspOutcome.NOT_PROCESSED),
                    n(rows, PspOutcome.UNKNOWN), n(rows, PspOutcome.CIRCUIT_OPEN),
                    answered == 0 ? 0 : Math.round(latencySum / answered)));
        }
        return result;
    }

    private List<TimelinePoint> timeline() {
        Instant end = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant start = end.minus(TIMELINE_WINDOW).plus(1, ChronoUnit.MINUTES);
        Map<Instant, long[]> buckets = new LinkedHashMap<>();
        for (Instant t = start; !t.isAfter(end); t = t.plus(1, ChronoUnit.MINUTES)) {
            buckets.put(t, new long[4]);
        }
        for (Payment p : payments.findTop5000ByCreatedAtAfterOrderByCreatedAtDesc(start)) {
            long[] b = buckets.get(p.getCreatedAt().truncatedTo(ChronoUnit.MINUTES));
            if (b == null) {
                continue;
            }
            switch (p.getStatus()) {
                case SUCCEEDED, REFUNDED -> {
                    b[0]++;
                    b[3] += p.getAmountMinor();
                }
                case FAILED -> b[1]++;
                case CREATED, PROCESSING -> b[2]++;
            }
        }
        List<TimelinePoint> points = new ArrayList<>();
        buckets.forEach((minute, b) -> points.add(new TimelinePoint(minute, b[0], b[1], b[2], b[3])));
        return points;
    }

    private static long count(Map<PaymentStatus, long[]> byStatus, PaymentStatus status) {
        long[] v = byStatus.get(status);
        return v == null ? 0 : v[0];
    }

    private static long amount(Map<PaymentStatus, long[]> byStatus, PaymentStatus status) {
        long[] v = byStatus.get(status);
        return v == null ? 0 : v[1];
    }

    private static long n(Map<PspOutcome, PspOutcomeTotal> rows, PspOutcome outcome) {
        PspOutcomeTotal row = rows.get(outcome);
        return row == null ? 0 : row.getCount();
    }

    public record OpsStats(Totals totals, List<PspStats> psps, List<TimelinePoint> timeline,
                           String eventTransport, long outboxLag, Instant generatedAt) {
    }

    /**
     * @param successRate share of completed payments that succeeded, in percent
     * @param grossVolume amount of all captured payments (refunds included), in paise
     */
    public record Totals(long payments, long succeeded, long failed, long inFlight, long refunded,
                         long grossVolume, long refundedVolume, double successRate, long avgPspLatencyMs) {
    }

    /** @param calls requests actually sent to the PSP (circuit-open skips are counted separately) */
    public record PspStats(String psp, String circuitState, float failureRate, long calls, long succeeded,
                           long declined, long notProcessed, long unknown, long circuitOpenSkips,
                           long avgLatencyMs) {
    }

    public record TimelinePoint(Instant minute, long succeeded, long failed, long inFlight, long volume) {
    }
}
