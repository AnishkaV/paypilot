package com.paypilot.gateway;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 1 keeps API keys in config to stay easy to run. A real deployment stores only a hash of
 * each key in a database, and Phase 2 adds OAuth2/JWT for the ops dashboard.
 */
@ConfigurationProperties("paypilot.gateway")
public record PayPilotGatewayProperties(
        List<Merchant> merchants,
        String adminKey,
        Routes routes,
        RateLimit rateLimit) {

    public record Merchant(String apiKey, String merchantId) {
    }

    public record Routes(String paymentService, String ledgerService, String mockPsp) {
    }

    /**
     * Token bucket: {@code replenishRate} tokens per second, up to {@code burstCapacity} saved up.
     */
    public record RateLimit(int replenishRate, int burstCapacity) {
    }
}
