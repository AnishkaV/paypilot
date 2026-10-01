package com.paypilot.payment.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under {@code paypilot.*} in application.yml, bound into immutable records. */
@ConfigurationProperties("paypilot")
public record PayPilotProperties(Psp psp, Outbox outbox, Sweeper sweeper, Idempotency idempotency, Events events) {

    /**
     * @param httpSubscribers local profile only: URLs that receive every outbox event by HTTP POST
     */
    public record Events(List<String> httpSubscribers) {
    }

    /**
     * @param providers PSP names in priority order. The router tries them left to right.
     */
    public record Psp(
            String baseUrl,
            List<String> providers,
            Duration connectTimeout,
            Duration readTimeout,
            CircuitBreaker circuitBreaker) {
    }

    public record CircuitBreaker(
            int slidingWindowSize,
            int minimumCalls,
            float failureRateThreshold,
            Duration waitInOpenState) {
    }

    public record Outbox(int batchSize) {
    }

    /**
     * @param stuckAfter a PROCESSING payment untouched for this long gets its outcome checked
     *                   with the PSP. Must be longer than {@code psp.readTimeout}.
     */
    public record Sweeper(Duration stuckAfter, int batchSize) {
    }

    public record Idempotency(Duration lockTtl) {
    }
}
