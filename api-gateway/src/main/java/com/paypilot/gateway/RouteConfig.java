package com.paypilot.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

/**
 * Routes are defined in Java rather than YAML so they are type-checked and easy to read.
 * Anything not listed here is not reachable through the gateway. Everything else on port 8080
 * (/, /assets/**) is the dashboard, served from src/main/resources/static.
 *
 * <pre>
 *   /api/v1/payments/**    merchant key   -> payment-service  (rate limited per merchant)
 *   /api/v1/ledger/**      admin key      -> ledger-service
 *   /api/v1/ops/chaos/**   admin key      -> mock-psp /admin/psps/**   (outage simulation)
 *   /api/v1/ops/**         admin key      -> payment-service /internal/**
 * </pre>
 */
@Configuration
public class RouteConfig {

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, PayPilotGatewayProperties properties,
                        RateLimiter<?> rateLimiter, KeyResolver principalKeyResolver) {
        PayPilotGatewayProperties.Routes targets = properties.routes();
        return builder.routes()
                .route("payments", r -> r.path("/api/v1/payments", "/api/v1/payments/**")
                        .filters(f -> f.requestRateLimiter(c -> c
                                .setRateLimiter(rateLimiter)
                                .setKeyResolver(principalKeyResolver)))
                        .uri(targets.paymentService()))
                .route("ledger", r -> r.path("/api/v1/ledger/**")
                        .uri(targets.ledgerService()))
                .route("ops-chaos", r -> r.order(-1).path("/api/v1/ops/chaos", "/api/v1/ops/chaos/**")
                        .filters(f -> f.rewritePath("/api/v1/ops/chaos(?<rest>.*)", "/admin/psps${rest}"))
                        .uri(targets.mockPsp()))
                .route("ops", r -> r.path("/api/v1/ops/**")
                        .filters(f -> f.rewritePath("/api/v1/ops/(?<rest>.*)", "/internal/${rest}"))
                        .uri(targets.paymentService()))
                .build();
    }

    /** Local profile: token buckets in memory. */
    @Bean
    @Profile("!infra")
    InMemoryRateLimiter inMemoryRateLimiter(PayPilotGatewayProperties properties) {
        return new InMemoryRateLimiter(properties.rateLimit().replenishRate(), properties.rateLimit().burstCapacity());
    }

    /**
     * Infra profile: token buckets in Redis, so the limit holds across every gateway instance.
     * If Redis is down it lets requests through rather than taking the whole API down.
     */
    @Bean
    @Profile("infra")
    RedisRateLimiter redisRateLimiter(PayPilotGatewayProperties properties) {
        return new RedisRateLimiter(properties.rateLimit().replenishRate(), properties.rateLimit().burstCapacity());
    }

    /** One bucket per merchant: a noisy merchant cannot use up everyone else's capacity. */
    @Bean
    KeyResolver principalKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.<String>getAttribute(ApiKeyAuthFilter.PRINCIPAL_ATTRIBUTE));
    }
}
