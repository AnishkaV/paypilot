package com.paypilot.mockpsp;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * In-memory stand-in for real payment providers. State is lost on restart, which is fine for a
 * simulator.
 *
 * <p>Payer values that force an outcome, handy for demos and tests:
 * <ul>
 *   <li>contains "decline" - the charge is declined with INSUFFICIENT_FUNDS</li>
 *   <li>contains "timeout" - the charge succeeds but the response is delayed past the caller's
 *       read timeout</li>
 * </ul>
 * Amounts above Rs 1,00,000 are declined with AMOUNT_LIMIT_EXCEEDED.
 */
@Service
public class PspSimulator {

    public static final List<String> PSPS = List.of("alpha", "beta", "gamma");

    static final long MAX_AMOUNT_MINOR = 10_000_000L;

    private static final Logger log = LoggerFactory.getLogger(PspSimulator.class);

    private final Map<String, PspBehavior> behaviors = new ConcurrentHashMap<>();
    private final Map<String, Charge> charges = new ConcurrentHashMap<>();
    private final long timeoutDelayMs;

    public PspSimulator(@Value("${mockpsp.timeout-delay-ms:5000}") long timeoutDelayMs) {
        this.timeoutDelayMs = timeoutDelayMs;
        reset();
    }

    public Charge charge(String psp, ChargeRequest request) {
        PspBehavior behavior = behavior(psp);
        if (behavior.down() || roll(behavior.errorRate())) {
            log.info("psp={} ref={} rejected with 503 (nothing charged)", psp, request.reference());
            throw new PspUnavailableException(psp);
        }

        // Idempotent by reference: a retry of the same charge returns the original result.
        Charge charge = charges.computeIfAbsent(key(psp, request.reference()),
                k -> decide(psp, request, behavior));
        sleep(behavior.latencyMs());

        boolean slowResponse = request.payer().toLowerCase(Locale.ROOT).contains("timeout")
                || roll(behavior.timeoutRate());
        if (slowResponse) {
            log.info("psp={} ref={} charged but answering late ({} ms)", psp, request.reference(), timeoutDelayMs);
            sleep(timeoutDelayMs);
        }
        return charge;
    }

    public Charge get(String psp, String reference) {
        behavior(psp);
        Charge charge = charges.get(key(psp, reference));
        if (charge == null) {
            throw new ChargeNotFoundException(psp, reference);
        }
        return charge;
    }

    public Charge refund(String psp, String reference) {
        if (behavior(psp).down()) {
            throw new PspUnavailableException(psp);
        }
        Charge charge = get(psp, reference);
        if (charge.status() == Charge.Status.REFUNDED) {
            return charge;
        }
        if (charge.status() != Charge.Status.SUCCEEDED) {
            throw new NotRefundableException(reference, charge.status());
        }
        Charge refunded = charges.computeIfPresent(key(psp, reference), (k, c) -> c.withStatus(Charge.Status.REFUNDED));
        log.info("psp={} ref={} refunded", psp, reference);
        return refunded;
    }

    public Map<String, PspBehavior> behaviors() {
        return Collections.unmodifiableMap(new TreeMap<>(behaviors));
    }

    public PspBehavior update(String psp, PspBehavior behavior) {
        behavior(psp);
        behaviors.put(psp, behavior);
        log.warn("psp={} behavior changed to {}", psp, behavior);
        return behavior;
    }

    public void reset() {
        PSPS.forEach(p -> behaviors.put(p, PspBehavior.healthy()));
    }

    private PspBehavior behavior(String psp) {
        PspBehavior behavior = behaviors.get(psp);
        if (behavior == null) {
            throw new UnknownPspException(psp);
        }
        return behavior;
    }

    private Charge decide(String psp, ChargeRequest request, PspBehavior behavior) {
        String declineCode = null;
        if (request.payer().toLowerCase(Locale.ROOT).contains("decline")) {
            declineCode = "INSUFFICIENT_FUNDS";
        } else if (request.amountMinor() > MAX_AMOUNT_MINOR) {
            declineCode = "AMOUNT_LIMIT_EXCEEDED";
        } else if (roll(behavior.declineRate())) {
            declineCode = "DO_NOT_HONOR";
        }
        Charge.Status status = declineCode == null ? Charge.Status.SUCCEEDED : Charge.Status.DECLINED;
        String pspReference = psp + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("psp={} ref={} amount={} status={} code={}", psp, request.reference(), request.amountMinor(), status, declineCode);
        return new Charge(pspReference, request.reference(), request.amountMinor(), request.currency(),
                status, declineCode, Instant.now());
    }

    private static String key(String psp, String reference) {
        return psp + ":" + reference;
    }

    private static boolean roll(double probability) {
        return probability > 0 && ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
