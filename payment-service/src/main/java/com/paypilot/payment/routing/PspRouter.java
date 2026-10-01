package com.paypilot.payment.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.paypilot.payment.config.PayPilotProperties;
import com.paypilot.payment.psp.ChargeCommand;
import com.paypilot.payment.psp.PspClient;
import com.paypilot.payment.psp.PspResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

/**
 * Decides which PSPs to try and keeps a circuit breaker per PSP.
 *
 * <p>Phase 1 routes by a fixed priority list and skips PSPs whose breaker is open.
 * Phase 2 replaces the fixed order with live success-rate scoring.
 *
 * <p>The breaker is driven by hand (tryAcquire / onSuccess / onError) instead of wrapping a
 * lambda, because a DECLINED payment is a successful PSP call and must not trip the breaker.
 */
@Component
public class PspRouter {

    private final List<String> providers;
    private final CircuitBreakerRegistry breakers;
    private final PspClient client;

    public PspRouter(PayPilotProperties properties, CircuitBreakerRegistry breakers, PspClient client) {
        this.providers = List.copyOf(properties.psp().providers());
        this.breakers = breakers;
        this.client = client;
    }

    /** PSPs in the order they should be tried. */
    public List<String> providers() {
        return providers;
    }

    /** False when the PSP's circuit is open. A true result must be followed by {@link #charge} or {@link #release}. */
    public boolean tryAcquire(String psp) {
        return breakers.circuitBreaker(psp).tryAcquirePermission();
    }

    public void release(String psp) {
        breakers.circuitBreaker(psp).releasePermission();
    }

    /** Calls the PSP and feeds the result into its circuit breaker. Call {@link #tryAcquire} first. */
    public PspResult charge(String psp, ChargeCommand command) {
        CircuitBreaker breaker = breakers.circuitBreaker(psp);
        long start = System.nanoTime();
        PspResult result = client.charge(psp, command);
        long duration = System.nanoTime() - start;
        if (result.outcome().isPspHealthy()) {
            breaker.onSuccess(duration, TimeUnit.NANOSECONDS);
        } else {
            breaker.onError(duration, TimeUnit.NANOSECONDS, new PspCallFailedException(psp, result.code()));
        }
        return result;
    }

    /** Current breaker state and failure rate per PSP, for dashboards and debugging. */
    public Map<String, PspHealth> health() {
        Map<String, PspHealth> health = new LinkedHashMap<>();
        for (String psp : providers) {
            CircuitBreaker breaker = breakers.circuitBreaker(psp);
            CircuitBreaker.Metrics metrics = breaker.getMetrics();
            health.put(psp, new PspHealth(breaker.getState().name(), metrics.getFailureRate(),
                    metrics.getNumberOfBufferedCalls(), metrics.getNumberOfFailedCalls()));
        }
        return health;
    }

    /**
     * @param failureRate percentage of failed calls in the window, or -1 until enough calls were made
     */
    public record PspHealth(String circuitState, float failureRate, int bufferedCalls, int failedCalls) {
    }

    static class PspCallFailedException extends RuntimeException {
        PspCallFailedException(String psp, String code) {
            super("PSP " + psp + " call failed: " + code, null, false, false);
        }
    }
}
